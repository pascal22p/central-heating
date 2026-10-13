package services

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.*
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, RequestId }

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ Materializer, RestartSettings, SystemMaterializer }
import org.apache.pekko.stream.connectors.mqtt.*
import org.apache.pekko.stream.connectors.mqtt.scaladsl.MqttSource
import org.apache.pekko.stream.scaladsl.{ RestartSource, Sink }
import org.apache.pekko.Done
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import config.AppConfig
import models.mqtt.{ TemperatureSensor, Trv }
import models.LoggingWithRequest
import queries.DevicesQueries

@Singleton
class MqttSubscriberService @Inject() (
    appConfig: AppConfig,
    lifecycle: ApplicationLifecycle,
    actorSystem: ActorSystem,
    devicesQueries: DevicesQueries,
    heatingControlService: HeatingControlService
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

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

  private val subscriptions = {
    logger.info(s"Subscribing to topics: ${appConfig.trvDevices} and ${appConfig.temperatureDevices}")
    MqttSubscriptions(
      appConfig.trvDevices.map { device =>
        device -> MqttQoS.AtLeastOnce
      }.toMap ++
        appConfig.temperatureDevices.map { device =>
          device -> MqttQoS.AtLeastOnce
        }.toMap
    )
  }

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
        implicit val hc: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId(UUID.randomUUID().toString)))

        logger.info(
          s"Received MQTT message on topic ${message.topic}: ${message.payload.utf8String}"
        )

        message.topic match {
          case topic if appConfig.trvDevices.contains(topic) =>
            handleTrvMessage(
              message.topic,
              message.payload.utf8String
            )

          case topic if appConfig.temperatureDevices.contains(topic) =>
            handleTemperatureMessage(
              message.topic,
              message.payload.utf8String
            )

          case topic => throw new RuntimeException(s"invalid topic `$topic`")
        }

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
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    Json.parse(payload).validate[Trv] match {
      case JsSuccess(state, _) =>
        val trv =
          state.copy(name = receivedTopic)

        devicesQueries
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

  private def handleTemperatureMessage(
      receivedTopic: String,
      payload: String
  )(implicit hc: HeaderCarrier): Future[Unit] = {
    logger.debug(payload)

    Json.parse(payload).validate[TemperatureSensor] match {
      case JsSuccess(state, _) =>
        val temperatureSensor =
          state.copy(name = receivedTopic)

        devicesQueries
          .saveTemperatureSensor(temperatureSensor)
          .flatMap { _ =>
            logger.info(
              s"Temperature sensor state saved for $receivedTopic: $temperatureSensor"
            )
            heatingControlService.onTemperatureSensorUpdate(temperatureSensor)
          }
          .recover {
            case NonFatal(error) =>
              logger.error(
                s"Failed to save temperature sensor state for $receivedTopic",
                error
              )
          }

      case JsError(errors) =>
        errors.foreach { case (path, errs) => logger.error(s"$path -> ${errs.map(_.message).mkString(", ")}") }
        throw new RuntimeException(JsError.toJson(JsError(errors)).toString)
    }
  }
}
