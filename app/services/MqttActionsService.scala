package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import cats.implicits.*
import config.AppConfig
import models.mqtt.{ TemperatureSensor, TemperatureSensorSelection, Trv }
import models.LoggingWithRequest
import queries.DevicesQueries

@Singleton
class MqttActionsService @Inject() (
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService,
    devicesQueries: DevicesQueries
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  def enforceSystemMode(trv: Trv)(implicit hc: HeaderCarrier): Future[Unit] = {
    val systemModeConfig = appConfig.trvSystemMode
    if (trv.systemMode == systemModeConfig) {
      Future.unit
    } else {
      logger.info(s"${trv.name} system mode is ${trv.systemMode}, enforcing $systemModeConfig")
      mqttPublisherService.updateSystemMode(trv.name, systemModeConfig).map(_ => ())
    }
  }

  def updateExternalSensor(temperatureDevice: TemperatureSensor)(implicit hc: HeaderCarrier): Future[Unit] = {
    val trvDevices = appConfig.temperatureSensorMapping.getOrElse(temperatureDevice.name, List.empty)

    trvDevices
      .traverse { trvDevice =>
        logger.debug(s"Updating external sensor for temperature device ${temperatureDevice.name} in trv $trvDevice")
        mqttPublisherService.updateExternalTemperature(trvDevice, temperatureDevice.temperature)
      }
      .map(_ => ())
  }

  def checkExternalSensor(trv: Trv)(implicit hc: HeaderCarrier): Future[Unit] = {
    val isExternalSensorConfig = appConfig.temperatureSensorMapping.exists(_._2.contains(trv.name))

    if (isExternalSensorConfig) {
      trv.temperatureSensorSelection match {
        case TemperatureSensorSelection.Internal =>
          logger.warn(s"trv ${trv.name} should have it's temperature_sensor_select to external not Internal")
          mqttPublisherService.updateTemperatureSensorSelection(trv.name, "remote_temperature").map(_ => ())
        case TemperatureSensorSelection.Remote =>
          logger.debug(s"temperature sensor is external for ${trv.name}")
          Future.unit
        case TemperatureSensorSelection.RemoteOffline =>
          logger.warn(s"Unexpected RemoteOffline status for ${trv.name}")
          val maybeSensor = appConfig.temperatureSensorMapping.find(_._2.contains(trv.name)).map(_._1)
          maybeSensor.fold(Future.unit) { sensor =>
            devicesQueries.getTemperatureSensor(sensor).flatMap {
              case Some(temperatureSensor) =>
                logger.info(
                  s"Updating external sensor for temperature device ${temperatureSensor.name} in trv ${trv.name}"
                )
                mqttPublisherService.updateExternalTemperature(trv.name, temperatureSensor.temperature).map(_ => ())
              case None =>
                logger.warn(s"No temperature sensor found for ${trv.name}")
                Future.unit
            }
          }

      }
    } else {
      logger.debug(s"No sensor mapping found for ${trv.name}")
      Future.unit
    }
  }
}
