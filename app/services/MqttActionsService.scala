package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import cats.implicits.*
import config.AppConfig
import models.mqtt.{ TemperatureSensor, TemperatureSensorSelection, Trv }
import models.LoggingWithRequest

@Singleton
class MqttActionsService @Inject() (
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService
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

  def enforceRemoteSensor(trv: Trv)(implicit hc: HeaderCarrier): Future[Unit] = {
    if (trv.temperatureSensorSelection == TemperatureSensorSelection.Internal) {
      logger.info(s"${trv.name} temperature sensor selection is ${trv.temperatureSensorSelection}, enforcing Remote")
      mqttPublisherService.updateTemperatureSensorSelection(trv.name, "remote_temperature").map(_ => ())
    } else {
      Future.unit
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

  def checkExternalSensor(trv: Trv)(implicit hc: HeaderCarrier): Unit = {
    val isExternalSensorConfig = appConfig.temperatureSensorMapping.exists(_._2.contains(trv.name))

    if (isExternalSensorConfig) {
      trv.temperatureSensorSelection match {
        case TemperatureSensorSelection.Internal =>
          logger.error(s"trv ${trv.name} should have it's temperature_sensor_select to external not Internal")
        case TemperatureSensorSelection.Remote =>
          logger.debug(s"temperature sensor is external for ${trv.name}")
        case TemperatureSensorSelection.RemoteOffline =>
          logger.error(s"Unexpected RemoteOffline status for ${trv.name}")
      }
    } else {
      logger.debug(s"No sensor mapping found for ${trv.name}")
    }
  }
}
