package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import cats.data.OptionT
import cats.implicits.*
import config.AppConfig
import models.mqtt.{ TemperatureSensor, TemperatureSensorSelection, Trv }
import models.LoggingWithRequest

@Singleton
class TemperatureSensorUpdateService @Inject() (
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  def onTemperatureUpdate(temperatureDevice: TemperatureSensor)(implicit hc: HeaderCarrier): OptionT[Future, Unit] = {
    logger.debug(s"Updating external sensor for temperature device $temperatureDevice")
    val maybeTrvDevice = appConfig.temperatureSensorMapping.get(temperatureDevice.name)

    OptionT(
      maybeTrvDevice
        .traverse { trvDevices =>
          trvDevices.traverse { trvDevice =>
            mqttPublisherService.updateExternalTemperature(trvDevice, temperatureDevice.temperature)
          }
        }
        .map(_ => Some(()))
    )
  }

  def checkExternalSensor(trv: Trv)(implicit hc: HeaderCarrier): OptionT[Future, Unit] = {
    val isExternalSensorConfig = appConfig.temperatureSensorMapping.exists(_._2.contains(trv.name))

    OptionT.some[Future](if (isExternalSensorConfig) {
      trv.temperatureSensorSelection match {
        case TemperatureSensorSelection.Internal =>
          logger.error(s"trv ${trv.name} should have it's temperature_sensor_select to external not Internal")
          ()
        case TemperatureSensorSelection.Remote =>
          ()
        case TemperatureSensorSelection.RemoteOffline =>
          logger.error(s"Unexpected RemoteOffline status for ${trv.name}")
          ()
      }
    } else {
      logger.debug(s"No sensor mapping found for ${trv.name}")
      ()
    })
  }
}
