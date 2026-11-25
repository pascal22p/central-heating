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
    mqttActionsService: MqttActionsService,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val trvTopics         = appConfig.trvDevices
  private val temperatureTopics = appConfig.temperatureDevices

  private def dropOnIdenticalState[A](maybePrevious: Option[A], current: A, topic: String)(using CanEqual[A, A]) = {
    if (maybePrevious.contains(current)) {
      Future.failed(AlreadyInCache(topic))
    } else {
      Future.unit
    }
  }

  private case class AlreadyInCache(topic: String) extends RuntimeException

  def handle(topic: String, payload: String)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (trvTopics.contains(topic)) {
      parseMqttBody[Trv](topic, payload).fold(Future.unit) { trv =>
        for {
          maybePrevious <- devicesQueries.getTrv(trv.name)
          _             <- dropOnIdenticalState(maybePrevious, trv, topic)
          _             <- devicesQueries.saveTrv(trv)
          _             <- heatingControlService.onTrvUpdate(trv)
          _             <- mqttActionsService.checkExternalSensor(trv)
          _             <- mqttActionsService.enforceSystemMode(trv)
          _             <- mqttActionsService.checkTrvSchedule(trv)
          _             <- mqttActionsService.checkTrvOverheating(trv)
        } yield ()
      }
    } else if (temperatureTopics.contains(topic)) {
      parseMqttBody[TemperatureSensor](topic, payload).fold(Future.unit) { sensor =>
        for {
          maybePrevious <- devicesQueries.getTemperatureSensor(sensor.name)
          _             <- dropOnIdenticalState(maybePrevious, sensor, topic)
          _             <- devicesQueries.saveTemperatureSensor(sensor)
          _             <- heatingControlService.onTemperatureSensorUpdate(sensor)
          _             <- mqttActionsService.updateExternalSensor(sensor)
        } yield ()
      }
    } else {
      logger.warn(s"Ignoring message on unexpected topic `$topic`")
      Future.unit
    }
  }.recover {
    case AlreadyInCache(topic) =>
      logger.debug(s"message from topic `$topic` already processed")
      ()
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
