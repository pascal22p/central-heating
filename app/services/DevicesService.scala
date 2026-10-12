package services

import javax.inject.{ Inject, Singleton }
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }

import cats.implicits.*
import config.AppConfig
import models.mqtt.{ TemperatureSensor, Trv }
import queries.DevicesQueries

@Singleton
class DevicesService @Inject() (
    devicesQueries: DevicesQueries,
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService
)(implicit ec: ExecutionContext)
    extends Logging {

  @SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
  def getTrvs: Future[Seq[Trv]] = {
    val devicesConfig = appConfig.trvDevices
    devicesQueries.getTrvs.flatMap { trvs =>
      devicesConfig
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
  def getTemperatureSensors: Future[Seq[TemperatureSensor]] = {
    val devicesConfig = appConfig.temperatureDevices
    devicesQueries.getTemperatureSensors.flatMap { sensors =>
      devicesConfig
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

}
