package services

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ JsError, JsObject, JsString, JsSuccess, Json, Reads }
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
    temperatureSensorUpdateService: TemperatureSensorUpdateService,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val trvTopics         = appConfig.trvDevices
  private val temperatureTopics = appConfig.temperatureDevices

  def handle(topic: String, payload: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (trvTopics.contains(topic)) {
      parseMqttBody[Trv](topic, payload).fold(Future.unit) { trv =>
        for {
          _ <- devicesQueries.saveTrv(trv)
          _ <- heatingControlService.onTrvUpdate(trv)
          _ <- Future.successful(temperatureSensorUpdateService.checkExternalSensor(trv))
        } yield ()
      }
    } else if (temperatureTopics.contains(topic)) {
      parseMqttBody[TemperatureSensor](topic, payload).fold(Future.unit) { sensor =>
        for {
          _ <- devicesQueries.saveTemperatureSensor(sensor)
          _ <- heatingControlService.onTemperatureSensorUpdate(sensor)
          _ <- temperatureSensorUpdateService.onTemperatureUpdate(sensor)
        } yield ()
      }
    } else {
      logger.warn(s"Ignoring message on unexpected topic `$topic`")
      Future.unit
    }
  }.recover {
    case NonFatal(ex) =>
      logger.error(ex.getMessage, ex)
      ()
  }

  private def parseMqttBody[A: Reads](topic: String, payload: String): Option[A] =
    Try(Json.parse(payload)) match {
      case Success(json: JsObject) =>
        (json + ("name" -> JsString(topic))).validate[A] match {
          case JsSuccess(value, _) => Some(value)
          case JsError(errors)     =>
            logger.error(s"Invalid payload on $topic: ${JsError.toJson(JsError(errors))}")
            None
        }
      case Success(_) =>
        logger.error(s"Payload on $topic is not a JSON object")
        None
      case Failure(error) =>
        logger.error(s"Unparseable payload on $topic", error)
        None
    }

}
