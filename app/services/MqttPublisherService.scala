package services

import java.util.{ Locale, UUID }
import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.DurationInt
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HeaderCarrier

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.{ Materializer, OverflowStrategy, QueueOfferResult, RestartSettings, SystemMaterializer }
import org.apache.pekko.stream.connectors.mqtt.*
import org.apache.pekko.stream.connectors.mqtt.scaladsl.MqttSink
import org.apache.pekko.stream.scaladsl.{ Keep, RestartSink, Source }
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import config.AppConfig
import models.mqtt.{ SystemMode, TemperatureSensorSelection }
import models.LoggingWithRequest

@Singleton
class MqttPublisherService @Inject() (
    appConfig: AppConfig,
    actorSystem: ActorSystem,
    lifecycle: ApplicationLifecycle
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private given Materializer =
    SystemMaterializer(actorSystem).materializer

  private val connectionSettings =
    MqttConnectionSettings(
      broker = appConfig.mqttBrokerUrl,
      clientId = s"${appConfig.mqttClientId}-publisher-${UUID.randomUUID()}",
      persistence = new MemoryPersistence
    )

  private val restartSettings =
    RestartSettings(
      minBackoff = 1.second,
      maxBackoff = 30.seconds,
      randomFactor = 0.2
    )

  // Recreate the MQTT sink if its stream fails or completes.
  private val restartingSink =
    RestartSink.withBackoff(restartSettings) { () =>
      logger.info("Starting MQTT publisher connection")

      MqttSink(
        connectionSettings,
        MqttQoS.AtLeastOnce
      )
    }

  // Materialize exactly once. The queue is the persistent input;
  // the sink is restarted independently if its connection fails.
  private val (publishQueue, streamCompletion) =
    Source
      .queue[MqttMessage](
        bufferSize = 100,
        overflowStrategy = OverflowStrategy.backpressure
      )
      .watchTermination()(Keep.both)
      .toMat(restartingSink)(Keep.left)
      .run()

  private def publish(message: MqttMessage): Future[Done] =
    publishQueue.offer(message).flatMap { result =>
      if (result eq QueueOfferResult.Enqueued) {
        Future.successful(Done)
      } else if (result eq QueueOfferResult.Dropped) {
        Future.failed(
          new RuntimeException("MQTT publishing queue dropped a message")
        )
      } else if (result eq QueueOfferResult.QueueClosed) {
        Future.failed(
          new RuntimeException("MQTT publishing queue is closed")
        )
      } else {
        result match {
          case failure: QueueOfferResult.Failure =>
            Future.failed(failure.cause)
          case _ =>
            Future.failed(
              new RuntimeException(s"Unexpected MQTT queue result: $result")
            )
        }
      }
    }

  def requestTrvState(device: String): Future[Done] =
    publish(
      MqttMessage(
        s"$device/get",
        ByteString(
          """{"temperature":"","occupied_heating_setpoint":""}"""
        )
      )
    )

  def requestTemperatureState(device: String): Future[Done] =
    publish(
      MqttMessage(
        s"$device/get",
        ByteString("""{"temperature":""}""")
      )
    )

  def updateExternalTemperature(
      device: String,
      temperature: Double
  )(implicit hc: HeaderCarrier): Future[Done] = {
    logger.info(
      s"Updating external temperature sensor on $device with $temperature"
    )

    publish(
      MqttMessage(
        s"$device/set",
        ByteString(s"""{"external_temperature_input":$temperature}""")
      )
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def updateSystemMode(
      device: String,
      systemMode: SystemMode
  )(implicit hc: HeaderCarrier): Future[Done] = {
    logger.info(
      s"Updating system mode on $device with $systemMode"
    )

    publish(
      MqttMessage(
        s"$device/set",
        ByteString(s"""{"system_mode":"${systemMode.toString.toLowerCase(Locale.ENGLISH)}"}""")
      )
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def updateTemperatureSensorSelection(
      device: String,
      temperatureSensorSelection: TemperatureSensorSelection
  )(implicit hc: HeaderCarrier): Future[Done] = {
    logger.info(
      s"Updating temperature sensor selection on $device with $temperatureSensorSelection"
    )

    publish(
      MqttMessage(
        s"$device/set",
        ByteString(
          s"""{"temperature_sensor_select":"${temperatureSensorSelection.toString.toLowerCase(Locale.ENGLISH)}"}"""
        )
      )
    )
  }

  lifecycle.addStopHook { () =>
    logger.info("Stopping MQTT publisher")

    publishQueue.complete()

    streamCompletion
      .recover {
        case NonFatal(error) =>
          logger.warn("MQTT publisher stream stopped with an error", error)
          Done
      }
      .map(_ => ())
  }

  logger.info(
    s"MQTT publisher service started: broker=${appConfig.mqttBrokerUrl}"
  )
}
