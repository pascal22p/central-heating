package services

import java.time.{ Clock, Duration }
import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ HeatingOverride, LoggingWithRequest, ThermostatState }
import models.nest.{ HvacStatus, NestDevice, ThermostatMode }
import queries.{ HeatingOverrideQueries, ThermostatStateQueries }

/**
 * Turns the Nest thermostat on and off.
 *
 *   - A manual override always wins: when one is active, the override is applied instead of the request.
 *   - Otherwise the thermostat is only touched when that changes its state, and not before it has spent the minimum
 *     time on (or off) since it was last switched, so that the boiler does not short-cycle.
 *   - An override is applied immediately, whatever the time since the last change.
 */
@Singleton
class NestService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    heatingOverrideQueries: HeatingOverrideQueries,
    thermostatStateQueries: ThermostatStateQueries,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  import NestService.Result

  private val noAction: Result = EitherT.rightT(())

  def turnOn(reason: String)(implicit hc: HeaderCarrier): Result =
    activeOverride(reason).flatMap {
      case Some(mode) => applyOverride(mode)
      case None       => switchOn(reason, respectMinimum = true)
    }

  def turnOff(reason: String)(implicit hc: HeaderCarrier): Result =
    activeOverride(reason).flatMap {
      case Some(mode) => applyOverride(mode)
      case None       => switchOff(reason, respectMinimum = true)
    }

  private def activeOverride(
      reason: String
  )(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Option[HeatingOverride]] =
    EitherT.liftF[Future, UpstreamErrorResponse, Option[HeatingOverride]](heatingOverrideQueries.getOverride).map {
      active =>
        active.foreach(mode => logger.info(s"Manual override ${mode.dbValue} is active, ignoring: $reason"))
        active
    }

  private def applyOverride(mode: HeatingOverride)(implicit hc: HeaderCarrier): Result =
    mode match {
      case HeatingOverride.Heat => switchOn("Manual override: heat", respectMinimum = false)
      case HeatingOverride.Idle => switchOff("Manual override: idle", respectMinimum = false)
    }

  private def switchOn(reason: String, respectMinimum: Boolean)(implicit hc: HeaderCarrier): Result =
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode != ThermostatMode.HEAT) {
        logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
        noAction
      } else if (device.traits.hvacStatus == HvacStatus.OFF) {
        minimumTimeLeft(device, ThermostatState.Idle, appConfig.heatingDemandMinimumOff, respectMinimum).flatMap {
          case Some(left) =>
            logger.info(s"$reason but the heating was turned off recently, waiting another ${left.toSeconds}s")
            noAction
          case None =>
            logger.info(s"$reason and heating is off, turning heating on")
            changeState(device, ThermostatState.Heat)
        }
      } else {
        logger.debug("The heating is already on")
        noAction
      }
    }

  private def switchOff(reason: String, respectMinimum: Boolean)(implicit hc: HeaderCarrier): Result =
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode != ThermostatMode.HEAT) {
        logger.info("The thermostat is OFF on summer mode. Nothing to turn off.")
        noAction
      } else if (device.traits.hvacStatus != HvacStatus.HEATING) {
        logger.debug("The heating is already off")
        noAction
      } else {
        minimumTimeLeft(device, ThermostatState.Heat, appConfig.heatingDemandMinimumOn, respectMinimum).flatMap {
          case Some(left) =>
            logger.info(s"$reason but the heating was turned on recently, waiting another ${left.toSeconds}s")
            noAction
          case None =>
            logger.info(s"$reason and heating is on, turning heating off")
            changeState(device, ThermostatState.Idle)
        }
      }
    }

  /** Sets the thermostat, then records the new state and the time, only if the thermostat accepted the change. */
  private def changeState(device: NestDevice, state: ThermostatState)(implicit hc: HeaderCarrier): Result = {
    val temperature = state match {
      case ThermostatState.Heat => appConfig.heatingDemandActiveTemperatureCelsius
      case ThermostatState.Idle => appConfig.heatingDemandStandbyTemperatureCelsius
    }
    nestConnector
      .setTemperature(temperature)
      .semiflatMap(_ => thermostatStateQueries.set(device.name, state, clock.instant()))
  }

  /** The time the thermostat still has to stay in `state`, if it was put in it less than `minimum` ago. */
  private def minimumTimeLeft(
      device: NestDevice,
      state: ThermostatState,
      minimum: FiniteDuration,
      respectMinimum: Boolean
  ): EitherT[Future, UpstreamErrorResponse, Option[FiniteDuration]] =
    if (respectMinimum) {
      EitherT.liftF[Future, UpstreamErrorResponse, Option[FiniteDuration]](
        thermostatStateQueries.get(device.name).map {
          _.filter(_.state == state).flatMap { change =>
            val elapsed = FiniteDuration(Duration.between(change.changedAt, clock.instant()).toMillis, MILLISECONDS)
            Option.when(elapsed < minimum)(minimum - elapsed)
          }
        }
      )
    } else {
      EitherT.rightT[Future, UpstreamErrorResponse](None)
    }
}

object NestService {
  type Result = EitherT[Future, UpstreamErrorResponse, Unit]
}
