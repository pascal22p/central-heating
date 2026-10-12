package services

import java.time.LocalTime
import javax.inject.{ Inject, Singleton }
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import models.nest.{ HvacStatus, ThermostatMode }

final case class ScheduleDay(periods: Seq[SchedulePeriod])
final case class SchedulePeriod(start: LocalTime, end: LocalTime, temperature: Double)

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    devicesService: DevicesService
)(implicit ec: ExecutionContext)
    extends Logging {

  val sensorSchedules: Map[String, ScheduleDay] = Map(
    "living-room-sensor1" -> ScheduleDay(
      Seq(
        SchedulePeriod(start = LocalTime.parse("06:00"), end = LocalTime.parse("22:00"), 20.5)
      )
    )
  )

  def onTrvUpdate(trv: Trv): Future[Unit] =
    if (!appConfig.heatingControlIsEnabled || !appConfig.trvControlIsEnabled) {
      logger.warn(
        s"Heating control disabled. Heating control: ${appConfig.heatingControlIsEnabled}, Trv control: ${appConfig.trvControlIsEnabled}"
      )
      Future.unit
    } else {
      logger.info("Heating control is enabled")
      given HeaderCarrier = HeaderCarrier()

      val action: EitherT[Future, UpstreamErrorResponse, Unit] =
        trv.runningState match {
          case RunningState.Heat =>
            logger.info(s"Trv `${trv.name}` is reporting HEAT")
            turnOnIfOff(trv)
          case RunningState.Idle =>
            logger.info(s"Trv `${trv.name}` is reporting IDLE")
            turnOffIfAllIdle
        }

      action.value
        .map {
          case Right(_) =>
            ()
          case Left(error) => logger.error(s"Failed to update heating for ${trv.name}: ${error.message}")
        }
        .recover {
          case NonFatal(e) => logger.error(s"Unexpected error updating heating for ${trv.name}", e)
        }
    }

  def onTemperatureSensorUpdate(sensor: TemperatureSensor): Future[Unit] =
    if (!appConfig.heatingControlIsEnabled || !appConfig.sensorControlIsEnabled) {
      logger.warn(
        s"Heating control disabled. Heating control: ${appConfig.heatingControlIsEnabled}, Sensor control: ${appConfig.sensorControlIsEnabled}"
      )
      Future.unit
    } else {
      logger.info("Heating control is enabled")

      given HeaderCarrier = HeaderCarrier()

      val now               = LocalTime.now
      val targetTemperature = sensorSchedules("living-room-sensor1").periods
        .find { period =>
          !now.isBefore(period.start) && !now.isAfter(period.end)
        }
        .map(_.temperature)
        .getOrElse(16.0)

      val action = if (sensor.temperature < targetTemperature) {
        nestConnector.getDevice().flatMap { device =>
          if (device.traits.thermostatMode == ThermostatMode.HEAT && device.traits.hvacStatus == HvacStatus.OFF) {
            logger.info(s"Heat requested by ${sensor.name} and heating is off, turning heating on")
            nestConnector.setTemperature(appConfig.heatingDemandActiveTemperatureCelsius)
          } else {
            if (device.traits.thermostatMode != ThermostatMode.HEAT) {
              logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
            }
            EitherT.rightT[Future, UpstreamErrorResponse](())
          }
        }
      } else {
        nestConnector.getDevice().flatMap { device =>
          if (device.traits.thermostatMode == ThermostatMode.HEAT && device.traits.hvacStatus == HvacStatus.HEATING) {
            logger.info(s"Idle requested by ${sensor.name} and heating is on, turning heating off")
            nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
          } else {
            if (device.traits.thermostatMode != ThermostatMode.HEAT) {
              logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
            }
            EitherT.rightT[Future, UpstreamErrorResponse](())
          }
        }
      }

      action.value
        .map {
          case Right(_) =>
            ()
          case Left(error) => logger.error(s"Failed to update heating for ${sensor.name}: ${error.message}")
        }
        .recover {
          case NonFatal(e) => logger.error(s"Unexpected error updating heating for ${sensor.name}", e)
        }
    }

  private def turnOnIfOff(trv: Trv)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] = {
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode == ThermostatMode.HEAT && device.traits.hvacStatus == HvacStatus.OFF) {
        logger.info(s"Heat requested by ${trv.name} and heating is off, turning heating on")
        nestConnector.setTemperature(appConfig.heatingDemandActiveTemperatureCelsius)
      } else {
        if (device.traits.thermostatMode != ThermostatMode.HEAT) {
          logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
        }
        if (device.traits.hvacStatus == HvacStatus.HEATING) {
          logger.info(s"The heating is already on")
        }
        EitherT.rightT[Future, UpstreamErrorResponse](())
      }
    }
  }

  private def turnOffIfAllIdle(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] =
    EitherT.liftF(devicesService.getTrvs).flatMap { trvs =>
      if (trvs.forall(_.runningState == RunningState.Idle)) {
        nestConnector.getDevice().flatMap { device =>
          if (device.traits.thermostatMode == ThermostatMode.HEAT && device.traits.hvacStatus == HvacStatus.HEATING) {
            logger.info(s"All TRVs are requesting idle and heating is on, turning heating off")
            nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
          } else {
            if (device.traits.thermostatMode != ThermostatMode.HEAT) {
              logger.info("The thermostat is OFF on summer mode.")
            }
            if (device.traits.hvacStatus == HvacStatus.HEATING) {
              logger.info(s"The heating is already on")
            }
            EitherT.rightT(())
          }
        }
      } else EitherT.rightT(())
    }

}
