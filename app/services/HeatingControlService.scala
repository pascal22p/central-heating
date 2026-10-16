package services

import java.time.{ Clock, LocalTime }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import cats.implicits.toTraverseOps
import config.AppConfig
import connectors.NestConnector
import models.{ HeatingOverride, LoggingWithRequest }
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import models.nest.{ HvacStatus, ThermostatMode }
import queries.{ HeatingOverrideQueries, ScheduleQueries }

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    devicesService: DevicesService,
    scheduleQueries: ScheduleQueries,
    heatingOverrideQueries: HeatingOverrideQueries,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private type Result = EitherT[Future, UpstreamErrorResponse, Unit]

  private val noAction: Result = EitherT.rightT(())

  def onTrvUpdate(trv: Trv)(implicit hc: HeaderCarrier): Future[Unit] =
    handle("Trv", trv.name, appConfig.trvControlIsEnabled) {
      trv.runningState match {
        case RunningState.Heat =>
          logger.info(s"Trv `${trv.name}` is reporting HEAT")
          turnHeatingOn(s"Heat requested by ${trv.name}")

        case RunningState.Idle | RunningState.Off =>
          logger.info(s"Trv `${trv.name}` is reporting ${trv.runningState}")
          turnHeatingOff(s"Idle requested by ${trv.name}")
      }
    }

  def onTemperatureSensorUpdate(sensor: TemperatureSensor)(implicit hc: HeaderCarrier): Future[Unit] =
    handle("Sensor", sensor.name, appConfig.sensorControlIsEnabled) {
      EitherT.liftF[Future, UpstreamErrorResponse, Double](targetTemperature(sensor.name)).flatMap { target =>
        if (sensor.temperature < target - appConfig.heatingControlOffset) {
          turnHeatingOn(s"Heat requested by ${sensor.name} (sensor ${sensor.temperature}deg, target ${target}deg)")
        } else {
          turnHeatingOff(s"Idle requested by ${sensor.name}")
        }
      }
    }

  /** Immediately forces the thermostat into the requested state, ignoring the TRVs and sensors. */
  def applyOverride(mode: HeatingOverride)(implicit hc: HeaderCarrier): Future[Unit] =
    execute("override", mode.dbValue, appConfig.heatingControlIsEnabled)(enforceOverride(mode))

  /** Used when an override is cleared: puts the thermostat back in the state the devices are asking for. */
  def applyAutomatic()(implicit hc: HeaderCarrier): Future[Unit] =
    execute("automatic", "override cleared", appConfig.heatingControlIsEnabled) {
      EitherT.liftF[Future, UpstreamErrorResponse, Seq[String]](devicesRequestingHeat).flatMap { requesting =>
        if (requesting.isEmpty) {
          turnHeatingOff("Override cleared and no device requests heat")
        } else {
          turnHeatingOn(s"Override cleared and heat requested by ${requesting.mkString(", ")}")
        }
      }
    }

  private def handle(kind: String, name: String, specificEnabled: Boolean)(
      action: => Result
  )(implicit hc: HeaderCarrier): Future[Unit] =
    execute(kind, name, appConfig.heatingControlIsEnabled && specificEnabled) {
      EitherT
        .liftF[Future, UpstreamErrorResponse, Option[HeatingOverride]](heatingOverrideQueries.getOverride)
        .flatMap {
          case Some(mode) =>
            logger.info(s"Manual override ${mode.dbValue} is active, ignoring the request from $name")
            enforceOverride(mode)
          case None =>
            action
        }
    }

  private def execute(kind: String, name: String, enabled: Boolean)(
      action: => Result
  )(implicit hc: HeaderCarrier): Future[Unit] =
    if (enabled) {
      logger.info(s"Heating control is enabled for $kind")
      action.value
        .map {
          case Right(_)    => logger.debug(s"Heating control finished successfully for $name")
          case Left(error) => logger.error(s"Failed to update heating for $name: ${error.message}")
        }
        .recover {
          case NonFatal(e) =>
            logger.error(s"Unexpected error updating heating for $name", e)
        }
    } else {
      logger.warn(s"Heating control disabled for $kind $name")
      Future.unit
    }

  private def enforceOverride(mode: HeatingOverride)(implicit hc: HeaderCarrier): Result =
    mode match {
      case HeatingOverride.Heat => turnHeatingOn("Manual override: heat")
      case HeatingOverride.Idle => turnHeatingOff("Manual override: idle", ignoreRequests = true)
    }

  private def turnHeatingOn(reason: String)(implicit hc: HeaderCarrier): Result =
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode != ThermostatMode.HEAT) {
        logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
        noAction
      } else if (device.traits.hvacStatus == HvacStatus.OFF) {
        logger.info(s"$reason and heating is off, turning heating on")
        nestConnector.setTemperature(appConfig.heatingDemandActiveTemperatureCelsius)
      } else {
        logger.debug("The heating is already on")
        noAction
      }
    }

  // Guard 1: Nest state. Guard 2: no device anywhere wants heat (skipped for a manual idle override).
  private def turnHeatingOff(reason: String, ignoreRequests: Boolean = false)(implicit hc: HeaderCarrier): Result =
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode != ThermostatMode.HEAT) {
        logger.info("The thermostat is OFF on summer mode. Nothing to turn off.")
        noAction
      } else if (device.traits.hvacStatus != HvacStatus.HEATING) {
        logger.debug("The heating is already off")
        noAction
      } else if (ignoreRequests) {
        logger.info(s"$reason and heating is on, turning heating off")
        nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
      } else {
        EitherT.liftF[Future, UpstreamErrorResponse, Seq[String]](devicesRequestingHeat).flatMap { requesting =>
          if (requesting.isEmpty) {
            logger.info(s"$reason and heating is on, turning heating off")
            nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
          } else {
            logger.info(s"Not turning heating off, heat requested by: ${requesting.mkString(", ")}")
            noAction
          }
        }
      }
    }

  private def devicesRequestingHeat(implicit hc: HeaderCarrier): Future[Seq[String]] =
    for {
      sensors <- devicesService.getTemperatureSensors
      targets <- sensors.traverse(s => targetTemperature(s.name).map(s -> _))
      trvs    <- devicesService.getTrvs
    } yield {
      val sensorsWanting = targets.collect {
        case (s, target) if s.temperature < target - appConfig.heatingControlOffset => s.name
      }
      val trvsWanting = trvs.filter(_.runningState == RunningState.Heat).map(_.name)
      sensorsWanting ++ trvsWanting
    }

  private def targetTemperature(sensorName: String)(implicit hc: HeaderCarrier): Future[Double] = {
    val now = LocalTime.now(clock)

    scheduleQueries.getSchedule(sensorName).map { schedule =>
      schedule.periods
        .find(period => !now.isBefore(period.start) && !now.isAfter(period.end))
        .map(_.temperature)
        .getOrElse {
          logger.info(s"No schedule period found for $sensorName and time $now.")
          appConfig.heatingDemandStandbyTemperatureCelsius
        }
    }
  }
}
