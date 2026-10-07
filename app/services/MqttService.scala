package services

import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.libs.json.*
import play.api.Logging
import scala.collection.concurrent.TrieMap
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

class MqttService @Inject() (
    appConfig: AppConfig,
    lifecycle: ApplicationLifecycle,
    actorSystem: ActorSystem
)(implicit ec: ExecutionContext)
    extends Logging {

  private given Materializer =
    SystemMaterializer(actorSystem).materializer

  private val brokerUrl =
    appConfig.mqttBrokerUrl

  private val clientId =
    s"${appConfig.mqttClientId}-${java.util.UUID.randomUUID()}"

  private val topic =
    appConfig.mqttTopic

  private val connectionSettings =
    MqttConnectionSettings(
      broker = brokerUrl,
      clientId = clientId,
      persistence = new MemoryPersistence
    ).withKeepAliveInterval(30.seconds)

  private val subscriptions =
    MqttSubscriptions(
      topic,
      MqttQoS.AtLeastOnce
    )

  private val trvs =
    TrieMap.empty[String, Trv]

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
      .map { message =>
        handleMessage(
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
    s"MQTT service started: broker=$brokerUrl topic=$topic clientId=$clientId"
  )

  def getTrvs: Seq[Trv] =
    trvs.values.toSeq

  def getTrv(name: String): Option[Trv] =
    trvs.get(name)

  private def handleMessage(
      receivedTopic: String,
      payload: String
  ): Unit = {
    extractDeviceName(receivedTopic) match {
      case Some(deviceName) if deviceName.endsWith("-trv") =>
        handleTrvMessage(
          deviceName,
          receivedTopic,
          payload
        )

      case _ =>
        ()
    }
  }

  private def extractDeviceName(
      receivedTopic: String
  ): Option[String] = {
    val prefix = appConfig.mqttTopic.stripSuffix("/+")

    if (receivedTopic.startsWith(s"$prefix/")) {
      val deviceName =
        receivedTopic.stripPrefix(s"$prefix/")

      if (deviceName.nonEmpty && !deviceName.contains("/")) {
        Some(deviceName)
      } else {
        None
      }
    } else {
      None
    }
  }

  private def handleTrvMessage(
      deviceName: String,
      receivedTopic: String,
      payload: String
  ): Unit = {
    logger.debug(payload)
    Json.parse(payload).validate[Trv] match {
      case JsSuccess(state, _) =>
        val trv =
          state.copy(name = deviceName)

        trvs.put(deviceName, trv) match {
          case None =>
            logger.info(
              s"Initial TRV state received for $deviceName: $trv"
            )
          case Some(previous) if previous != trv =>
            logStateChanges(deviceName, previous, trv)
          case Some(_) =>
            ()
        }

      case JsError(errors) =>
        logger.warn(s"Invalid TRV state on $receivedTopic: ${JsError.toJson(errors)}")
    }
  }

  private def logStateChanges(
      deviceName: String,
      previous: Trv,
      current: Trv
  ): Unit = {
    val names          = previous.productElementNames.toSeq
    val previousValues = previous.productIterator.toSeq
    val currentValues  = current.productIterator.toSeq

    val changes =
      names
        .zip(previousValues)
        .zip(currentValues)
        .collect {
          case ((name, previousValue), currentValue) if !previousValue.equals(currentValue) =>
            s"$name: $previousValue -> $currentValue"
        }

    if (changes.nonEmpty) {
      logger.info(
        s"TRV state changed for $deviceName: ${changes.mkString(", ")}"
      )
    }
  }
}
