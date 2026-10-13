package services

import java.util.UUID
import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }
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
import models.LoggingWithRequest

@Singleton
class MqttSubscriberService @Inject() (
    appConfig: AppConfig,
    lifecycle: ApplicationLifecycle,
    actorSystem: ActorSystem,
    mqttMessageHandlerService: MqttMessageHandlerService
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private given Materializer = SystemMaterializer(actorSystem).materializer

  private val brokerUrl = appConfig.mqttBrokerUrl
  private val clientId  = s"${appConfig.mqttClientId}-${UUID.randomUUID()}"

  private val trvTopics         = appConfig.trvDevices
  private val temperatureTopics = appConfig.temperatureDevices

  private val connectionSettings =
    MqttConnectionSettings(
      broker = brokerUrl,
      clientId = clientId,
      persistence = new MemoryPersistence
    ).withKeepAliveInterval(30.seconds)

  private val subscriptions = {
    logger.info(s"Subscribing to topics: $trvTopics and $temperatureTopics")
    MqttSubscriptions((trvTopics ++ temperatureTopics).map(_ -> MqttQoS.AtLeastOnce).toMap)
  }

  private val restartSettings =
    RestartSettings(minBackoff = 1.second, maxBackoff = 30.seconds, randomFactor = 0.2)

  private val stream: Future[Done] =
    RestartSource
      .withBackoff(restartSettings) { () =>
        logger.info(s"Connecting to MQTT broker $brokerUrl")

        MqttSource
          .atMostOnce(connectionSettings, subscriptions, bufferSize = 8)
          .watchTermination() { (_, termination) =>
            termination.onComplete {
              case Success(_)     => logger.info(s"MQTT connection terminated: $brokerUrl")
              case Failure(error) => logger.error(s"MQTT connection lost: $brokerUrl", error)
            }
            termination
          }
      }
      .mapAsync(1) { message =>
        given HeaderCarrier = HeaderCarrier(requestId = Some(RequestId(UUID.randomUUID().toString)))

        val topic   = message.topic
        val payload = message.payload.utf8String

        logger.debug(s"Received MQTT message on topic $topic: $payload")

        mqttMessageHandlerService.handle(
          topic,
          payload
        )
      }
      .runWith(Sink.ignore)

  lifecycle.addStopHook { () =>
    stream
      .recover {
        case NonFatal(e) =>
          logger.warn("MQTT stream stopped with an error", e)
          Done
      }
      .map(_ => ())
  }

  logger.info(
    s"MQTT service started: broker=$brokerUrl trvs=$trvTopics temperatureSensors=$temperatureTopics clientId=$clientId"
  )
}
