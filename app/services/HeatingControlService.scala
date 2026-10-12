package services

import java.time.LocalTime
import javax.inject.{ Inject, Singleton }
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import cats.implicits.toTraverseOps
import config.AppConfig
import connectors.NestConnector
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import models.nest.{ HvacStatus, ThermostatMode }
import queries.ScheduleQueries

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    devicesService: DevicesService,
    scheduleQueries: ScheduleQueries
)(implicit ec: ExecutionContext)
    extends Logging {

  def onTrvUpdate(trv: Trv): Future[Unit] =
    if (!appConfig.heatingControlIsEnabled || !appConfig.trvControlIsEnabled) {
      logger.warn(
        s"Heating control disabled. Heating control: ${appConfig.heatingControlIsEnabled}, Trv control: ${appConfig.trvControlIsEnabled}"
      )
      Future.unit
    } else {
      logger.info("Heating control is enabled for Trvs")
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
      logger.info("Heating control is enabled for temperature Sensors")

      given HeaderCarrier = HeaderCarrier()

      val now = LocalTime.now

      val targetTemperatureF = scheduleQueries.getSchedule(sensor.name).map { schedule =>
        schedule.periods
          .find { period =>
            !now.isBefore(period.start) && !now.isAfter(period.end)
          }
          .map(_.temperature)
          .getOrElse {
            logger.info(s"No schedule period found for ${sensor.name} and time $now.")
            appConfig.heatingDemandStandbyTemperatureCelsius
          }
      }

      targetTemperatureF.flatMap { targetTemperature =>
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
              EitherT.liftF(canHeatingBeTurnedOff.map {
                case true =>
                  logger.info(s"Idle requested by ${sensor.name} and heating is on, turning heating off")
                  nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)

                case false =>
                  EitherT.rightT[Future, UpstreamErrorResponse](())
              })
            } else {
              if (device.traits.thermostatMode != ThermostatMode.HEAT) {
                logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
              } else {
                logger.debug("The thermostat is already heating")
              }
              EitherT.rightT[Future, UpstreamErrorResponse](())
            }
          }
        }

        action.value
          .map {
            case Right(_) =>
              logger.debug("Heating control finished successfully for temperature Sensors")
              ()
            case Left(error) => logger.error(s"Failed to update heating for ${sensor.name}: ${error.message}")
          }
          .recover {
            case NonFatal(e) => logger.error(s"Unexpected error updating heating for ${sensor.name}", e)
          }
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

  private def canHeatingBeTurnedOff: Future[Boolean] = {
    for {
      sensors   <- devicesService.getTemperatureSensors
      schedules <- sensors
        .traverse(sensor => scheduleQueries.getSchedule(sensor.name).map(sensor.name -> _))
        .map(_.toMap)
      trvs <- devicesService.getTrvs
    } yield {
      val now          = LocalTime.now
      val sensorsCheck = sensors.forall { sensor =>
        val temperatureTarget = schedules
          .get(sensor.name)
          .flatMap(_.periods.find(period => !now.isBefore(period.start) && !now.isAfter(period.end)))
          .fold(0.0)(_.temperature)
        if (sensor.temperature <= temperatureTarget) {
          logger.info(s"Sensor ${sensor.name} is requesting HEAT")
        }
        sensor.temperature > temperatureTarget
      }

      trvs.filter(_.runningState != RunningState.Idle).foreach { trv =>
        logger.info(s"Trv ${trv.name} is requesting HEAT")
      }

      trvs.forall(_.runningState == RunningState.Idle) && sensorsCheck
    }

  }

}
