package services

import java.time.{ Clock, LocalDateTime }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import cats.implicits.*
import config.AppConfig
import models.mqtt.{ TemperatureSensor, Trv }
import models.LoggingWithRequest
import queries.{ DevicesQueries, ScheduleQueries }

@Singleton
class DevicesService @Inject() (
    devicesQueries: DevicesQueries,
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService,
    scheduleQueries: ScheduleQueries,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  @SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
  def getTrvs(implicit hc: HeaderCarrier): Future[Seq[Trv]] = {
    val devicesConfig = appConfig.trvDevices
    devicesQueries.getTrvs.flatMap { trvs =>
      devicesConfig.toSeq
        .filterNot(device => trvs.exists(_.name == device))
        .traverse { device =>
          logger.info(s"Requesting TRV state for device: $device")
          for {
            _   <- mqttPublisherService.requestTrvState(device)
            _   <- Future.successful(Thread.sleep(5000))
            trv <- devicesQueries.getTrv(device)
          } yield trv
        }
        .map(_.flatten ++ trvs)
    }
  }

  @SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
  def getTemperatureSensors(implicit hc: HeaderCarrier): Future[Seq[TemperatureSensor]] = {
    val devicesConfig = appConfig.temperatureDevices
    devicesQueries.getTemperatureSensors.flatMap { sensors =>
      devicesConfig.toSeq
        .filterNot(device => sensors.exists(_.name == device))
        .traverse { device =>
          logger.info(s"Requesting sensor state for device: $device")
          for {
            _      <- mqttPublisherService.requestTemperatureState(device)
            _      <- Future.successful(Thread.sleep(5000))
            sensor <- devicesQueries.getTemperatureSensor(device)
          } yield sensor
        }
        .map(_.flatten ++ sensors)
    }
  }

  def targetTemperature(sensorName: String, activeGroup: Int)(implicit hc: HeaderCarrier): Future[Double] = {
    val now = LocalDateTime.now(clock)

    scheduleQueries
      .getScheduleByTopic(sensorName, activeGroup)
      .fold {
        logger.warn(s"No schedule period found for $sensorName and time $now.")
        appConfig.heatingDemandStandbyTemperatureCelsius
      } { schedule =>
        val periods = now.getDayOfWeek.getValue match {
          case day if day < 6 => schedule.weekdays
          case _              => schedule.weekend
        }

        periods.filter(_.start.isBefore(now.toLocalTime)).maxBy(_.start).temperature
      }
  }

}
