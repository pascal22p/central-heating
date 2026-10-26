package services

import java.time.{ Clock, LocalTime }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import cats.implicits.toTraverseOps
import config.AppConfig
import models.{ HeatDemand, HeatingOverride, LoggingWithRequest, SensorHeatDemand }
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import queries.{ ScheduleQueries, SensorHeatDemandQueries }
import services.NestService.Result

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestService: NestService,
    devicesService: DevicesService,
    scheduleQueries: ScheduleQueries,
    sensorHeatDemandQueries: SensorHeatDemandQueries,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val noAction: Result = EitherT.rightT(())

  /** Applies the hysteresis to the sensor reading, starting from the stored demand (idle when there is none). */
  private def nextDemand(stored: Option[SensorHeatDemand], sensor: TemperatureSensor, target: Double): HeatDemand =
    HeatDemand.next(
      stored.map(_.demand).getOrElse(HeatDemand.Idle),
      sensor.temperature,
      target,
      appConfig.heatingControlHysteresis
    )

  /**
   * Used when a sensor reports: works out its demand and stores it, with the time, only when it differs from the
   * stored one, so `changed_at` is always the time of the last change.
   */
  private def updateSensorDemand(sensor: TemperatureSensor, target: Double): Future[HeatDemand] =
    for {
      stored <- sensorHeatDemandQueries.get(sensor.name)
      next = nextDemand(stored, sensor, target)
      _ <-
        if (stored.exists(_.demand == next)) {
          Future.unit
        } else {
          sensorHeatDemandQueries.set(sensor.name, next, clock.instant())
        }
    } yield next

  def onTrvUpdate(trv: Trv)(implicit hc: HeaderCarrier): Future[Unit] =
    execute("Trv", trv.name, appConfig.heatingControlIsEnabled && appConfig.trvControlIsEnabled) {
      trv.runningState match {
        case RunningState.Heat =>
          logger.info(s"Trv `${trv.name}` is reporting HEAT")
          nestService.turnOn(s"Heat requested by ${trv.name}")

        case RunningState.Idle | RunningState.Off =>
          logger.info(s"Trv `${trv.name}` is reporting ${trv.runningState}")
          turnOffUnlessRequested(s"Idle requested by ${trv.name}")
      }
    }

  def onTemperatureSensorUpdate(sensor: TemperatureSensor)(implicit hc: HeaderCarrier): Future[Unit] =
    execute("Sensor", sensor.name, appConfig.heatingControlIsEnabled && appConfig.sensorControlIsEnabled) {
      EitherT.liftF[Future, UpstreamErrorResponse, Double](targetTemperature(sensor.name)).flatMap { target =>
        EitherT.liftF[Future, UpstreamErrorResponse, HeatDemand](updateSensorDemand(sensor, target)).flatMap {
          case HeatDemand.Heat =>
            val details = s"sensor ${sensor.temperature}deg, target ${target}deg"
            nestService.turnOn(s"Heat requested by ${sensor.name} ($details)")
          case HeatDemand.Idle =>
            turnOffUnlessRequested(s"Idle requested by ${sensor.name}")
        }
      }
    }

  /** Immediately forces the thermostat into the requested state, ignoring the TRVs and sensors. */
  def applyOverride(mode: HeatingOverride)(implicit hc: HeaderCarrier): Future[Unit] =
    execute("override", mode.dbValue, appConfig.heatingControlIsEnabled) {
      mode match {
        case HeatingOverride.Heat => nestService.turnOn("Override set to heat")
        case HeatingOverride.Idle => nestService.turnOff("Override set to idle")
      }
    }

  /** Used when an override is cleared: puts the thermostat back in the state the devices are asking for. */
  def applyAutomatic()(implicit hc: HeaderCarrier): Future[Unit] =
    execute("automatic", "override cleared", appConfig.heatingControlIsEnabled) {
      EitherT.liftF[Future, UpstreamErrorResponse, Seq[String]](devicesRequestingHeat).flatMap { requesting =>
        if (requesting.isEmpty) {
          nestService.turnOff("Override cleared and no device requests heat")
        } else {
          nestService.turnOn(s"Override cleared and heat requested by ${requesting.mkString(", ")}")
        }
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

  /** The heating is only turned off when no device anywhere still wants heat. */
  private def turnOffUnlessRequested(reason: String)(implicit hc: HeaderCarrier): Result =
    EitherT.liftF[Future, UpstreamErrorResponse, Seq[String]](devicesRequestingHeat).flatMap { requesting =>
      if (requesting.isEmpty) {
        nestService.turnOff(reason)
      } else {
        logger.debug(s"Not turning heating off, heat requested by: ${requesting.mkString(", ")}")
        noAction
      }
    }

  private def devicesRequestingHeat(implicit hc: HeaderCarrier): Future[Seq[String]] =
    for {
      sensors <- devicesService.getTemperatureSensors
      targets <- sensors.traverse(s => targetTemperature(s.name).map(s -> _))
      demands <- targets.traverse {
        case (s, target) =>
          sensorHeatDemandQueries.get(s.name).map(stored => s.name -> nextDemand(stored, s, target))
      }
      trvs <- devicesService.getTrvs
    } yield {
      val sensorsWanting = demands.collect { case (name, HeatDemand.Heat) => name }
      val trvsWanting    = trvs.filter(_.runningState == RunningState.Heat).map(_.name)
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
