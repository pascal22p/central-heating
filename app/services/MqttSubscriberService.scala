package services

import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.*
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ Materializer, RestartSettings, SystemMaterializer }
import org.apache.pekko.stream.connectors.mqtt.*
import org.apache.pekko.stream.connectors.mqtt.scaladsl.MqttSource
import org.apache.pekko.stream.scaladsl.{ RestartSource, Sink }
import org.apache.pekko.Done
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import config.AppConfig
import models.Trv
import queries.TrvQueries

@Singleton
class MqttSubscriberService @Inject() (
    appConfig: AppConfig,
    lifecycle: ApplicationLifecycle,
    actorSystem: ActorSystem,
    trvQueries: TrvQueries,
    heatingControlService: HeatingControlService
)(implicit ec: ExecutionContext)
    extends Logging {

  private given Materializer =
    SystemMaterializer(actorSystem).materializer

  private val brokerUrl =
    appConfig.mqttBrokerUrl

  private val clientId =
    s"${appConfig.mqttClientId}-${java.util.UUID.randomUUID()}"

  private val connectionSettings =
    MqttConnectionSettings(
      broker = brokerUrl,
      clientId = clientId,
      persistence = new MemoryPersistence
    ).withKeepAliveInterval(30.seconds)

  private val subscriptions =
    MqttSubscriptions(
      appConfig.trvDevices.map { device =>
        device -> MqttQoS.AtLeastOnce
      }.toMap
    )

  private val restartSettings =
    RestartSettings(
      minBackoff = 1.second,
      maxBackoff = 30.seconds,
      randomFactor = 0.2
    )

  private val stream: Future[Done] =
    RestartSource
      .withBackoff(restartSettings) { () =>
        logger.info(
          s"Connecting to MQTT broker $brokerUrl"
        )

        MqttSource
          .atMostOnce(
            connectionSettings,
            subscriptions,
            bufferSize = 8
          )
          .watchTermination() { (_, termination) =>
            termination.failed.foreach { error =>
              logger.error(
                s"MQTT connection lost: $brokerUrl",
                error
              )
            }

            termination.foreach { _ =>
              logger.info(
                s"MQTT connection terminated: $brokerUrl"
              )
            }

            termination
          }
      }
      .mapAsync(1) { message =>
        logger.info(
          s"Received MQTT message on topic ${message.topic}: ${message.payload.utf8String}"
        )

        handleTrvMessage(
          message.topic,
          message.payload.utf8String
        )
      }
      .runWith(Sink.ignore)

  lifecycle.addStopHook { () =>
    stream
      .recover {
        case NonFatal(e) =>
          logger.warn(
            "MQTT stream stopped with an error",
            e
          )
          Done
      }
      .map(_ => ())
  }

  logger.info(
    s"MQTT service started: broker=$brokerUrl devices=${appConfig.trvDevices} clientId=$clientId"
  )

  private def handleTrvMessage(
      receivedTopic: String,
      payload: String
  ): Future[Unit] = {
    logger.debug(payload)

    Json.parse(payload).validate[Trv] match {
      case JsSuccess(state, _) =>
        val trv =
          state.copy(name = receivedTopic)

        trvQueries
          .saveTrv(trv)
          .flatMap { _ =>
            logger.info(
              s"TRV state saved for $receivedTopic: $trv"
            )
            heatingControlService.onTrvUpdate(trv)
          }
          .recover {
            case NonFatal(error) =>
              logger.error(
                s"Failed to save TRV state for $receivedTopic",
                error
              )
          }

      case JsError(errors) =>
        errors.foreach { case (path, errs) => logger.error(s"$path -> ${errs.map(_.message).mkString(", ")}") }
        throw new RuntimeException(JsError.toJson(JsError(errors)).toString)
    }
  }
}
