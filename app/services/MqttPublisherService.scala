package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.Future

import uk.gov.hmrc.http.HeaderCarrier

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.connectors.mqtt.*
import org.apache.pekko.stream.connectors.mqtt.scaladsl.MqttSink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.Materializer
import org.apache.pekko.util.ByteString
import org.apache.pekko.Done
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

import config.AppConfig
import models.LoggingWithRequest

@Singleton
class MqttPublisherService @Inject() (
    appConfig: AppConfig,
    actorSystem: ActorSystem
) extends LoggingWithRequest {

  private given Materializer =
    Materializer(actorSystem)

  private val connectionSettings =
    MqttConnectionSettings(
      broker = appConfig.mqttBrokerUrl,
      clientId = s"${appConfig.mqttClientId}-publisher-${java.util.UUID.randomUUID()}",
      persistence = new MemoryPersistence
    )

  private val sink =
    MqttSink(
      connectionSettings,
      MqttQoS.AtLeastOnce
    )

  def requestTrvState(device: String): Future[Done] =
    Source
      .single(
        MqttMessage(
          s"${device}/get",
          ByteString("""{"temperature":"","occupied_heating_setpoint":"","battery":""}""")
        )
      )
      .runWith(sink)

  def requestTemperatureState(device: String): Future[Done] =
    Source
      .single(
        MqttMessage(
          s"${device}/get",
          ByteString("""{"temperature":""}""")
        )
      )
      .runWith(sink)

  def updateExternalTemperature(device: String, temperature: Double)(implicit hc: HeaderCarrier): Future[Done] = {
    logger.info(s"Updating external temperature sensor on $device with $temperature")
    Source
      .single(
        MqttMessage(
          s"$device/set",
          ByteString(s"""{"external_temperature_input":$temperature}""")
        )
      )
      .runWith(sink)
  }
}
