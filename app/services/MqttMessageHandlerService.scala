package services

import javax.inject.{ Inject, Singleton }
import play.api.libs.json.{ JsError, JsSuccess, Json, Reads }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

import uk.gov.hmrc.http.HeaderCarrier

import cats.data.OptionT
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

  def handle(topic: String, payload: String)(implicit hc: HeaderCarrier): Future[Unit] =
    if (trvTopics.contains(topic)) {
      (for {
        trvRaw <- OptionT.fromOption[Future](parseMqttBody[Trv](topic, payload))
        trv = trvRaw.copy(name = topic)
        _ <- OptionT.liftF(devicesQueries.saveTrv(trv))
        _ <- OptionT.liftF(heatingControlService.onTrvUpdate(trv))
      } yield ()).getOrElse(())
    } else if (temperatureTopics.contains(topic)) {
      (for {
        sensorRaw <- OptionT.fromOption[Future](parseMqttBody[TemperatureSensor](topic, payload))
        sensor = sensorRaw.copy(name = topic)
        _ <- OptionT.liftF(devicesQueries.saveTemperatureSensor(sensor))
        _ <- OptionT.liftF(heatingControlService.onTemperatureSensorUpdate(sensor))
        _ <- temperatureSensorUpdateService.onTemperatureUpdate(sensor)
      } yield ()).getOrElse(())
    } else {
      logger.warn(s"Ignoring message on unexpected topic `$topic`")
      Future.unit
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
