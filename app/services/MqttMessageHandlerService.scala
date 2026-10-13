package services

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ JsError, JsSuccess, Json, Reads }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HeaderCarrier

import config.AppConfig
import models.mqtt.{ TemperatureSensor, Trv }
import models.LoggingWithRequest
import queries.DevicesQueries

@Singleton
class MqttMessageHandlerService @Inject() (
    devicesQueries: DevicesQueries,
    heatingControlService: HeatingControlService,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val trvTopics         = appConfig.trvDevices
  private val temperatureTopics = appConfig.temperatureDevices

  def handle(topic: String, payload: String)(implicit hc: HeaderCarrier): Future[Unit] =
    if (trvTopics.contains(topic)) {
      handleMqttMessage[Trv](topic, payload)(
        withName = (trv, name) => trv.copy(name = name),
        save = devicesQueries.saveTrv,
        onUpdate = heatingControlService.onTrvUpdate
      )
    } else if (temperatureTopics.contains(topic)) {
      handleMqttMessage[TemperatureSensor](topic, payload)(
        withName = (sensor, name) => sensor.copy(name = name),
        save = devicesQueries.saveTemperatureSensor,
        onUpdate = heatingControlService.onTemperatureSensorUpdate
      )
    } else {
      logger.warn(s"Ignoring message on unexpected topic `$topic`")
      Future.unit
    }

  private def handleMqttMessage[A: Reads](topic: String, payload: String)(
      withName: (A, String) => A,
      save: A => Future[?],
      onUpdate: A => Future[Unit]
  ): Future[Unit] =
    parseMqttBody[A](topic, payload).fold(Future.unit) { parsed =>
      val device = withName(parsed, topic)

      save(device)
        .flatMap { _ =>
          logger.debug(s"State saved for $topic: $device")
          onUpdate(device)
        }
        .recover {
          case NonFatal(error) =>
            logger.error(s"Failed to process update for $topic", error)
        }
    }

  private def parseMqttBody[A: Reads](topic: String, payload: String): Option[A] =
    Try(Json.parse(payload).validate[A]) match {
      case Success(JsSuccess(value, _)) =>
        Some(value)
      case Success(JsError(errors)) =>
        logger.error(s"Invalid payload on $topic: ${JsError.toJson(JsError(errors))}")
        None
      case Failure(error) =>
        logger.error(s"Unparseable payload on $topic", error)
        None
    }

}
