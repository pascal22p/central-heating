package services

import java.time.{ Clock, LocalTime }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import cats.implicits.toTraverseOps
import config.AppConfig
import models.{ HeatingOverride, LoggingWithRequest }
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import queries.ScheduleQueries
import services.NestService.Result

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestService: NestService,
    devicesService: DevicesService,
    scheduleQueries: ScheduleQueries,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val noAction: Result = EitherT.rightT(())

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
        if (sensor.temperature < target) {
          val details = s"sensor ${sensor.temperature}deg, target ${target}deg"
          nestService.turnOn(s"Heat requested by ${sensor.name} ($details)")
        } else {
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
      trvs    <- devicesService.getTrvs
    } yield {
      val sensorsWanting = targets.collect { case (s, target) if s.temperature < target => s.name }
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
