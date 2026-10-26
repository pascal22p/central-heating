package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ HeatingOverride, LoggingWithRequest }
import models.nest.{ HvacStatus, ThermostatMode }
import queries.HeatingOverrideQueries

/**
 * Turns the Nest thermostat on and off. A manual override always wins: when one is active, the override is applied
 * instead of the request. Otherwise the thermostat is only touched when that changes its state.
 */
@Singleton
class NestService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    heatingOverrideQueries: HeatingOverrideQueries
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  import NestService.Result

  private val noAction: Result = EitherT.rightT(())

  def turnOn(reason: String)(implicit hc: HeaderCarrier): Result =
    activeOverride(reason).flatMap {
      case Some(mode) => applyOverride(mode)
      case None       => switchOn(reason)
    }

  def turnOff(reason: String)(implicit hc: HeaderCarrier): Result =
    activeOverride(reason).flatMap {
      case Some(mode) => applyOverride(mode)
      case None       => switchOff(reason)
    }

  private def activeOverride(reason: String)(implicit
      hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Option[HeatingOverride]] =
    EitherT.liftF[Future, UpstreamErrorResponse, Option[HeatingOverride]](heatingOverrideQueries.getOverride).map {
      active =>
        active.foreach(mode => logger.info(s"Manual override ${mode.dbValue} is active, ignoring: $reason"))
        active
    }

  private def applyOverride(mode: HeatingOverride)(implicit hc: HeaderCarrier): Result =
    mode match {
      case HeatingOverride.Heat => switchOn("Manual override: heat")
      case HeatingOverride.Idle => switchOff("Manual override: idle")
    }

  private def switchOn(reason: String)(implicit hc: HeaderCarrier): Result =
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

  private def switchOff(reason: String)(implicit hc: HeaderCarrier): Result =
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode != ThermostatMode.HEAT) {
        logger.info("The thermostat is OFF on summer mode. Nothing to turn off.")
        noAction
      } else if (device.traits.hvacStatus != HvacStatus.HEATING) {
        logger.debug("The heating is already off")
        noAction
      } else {
        logger.info(s"$reason and heating is on, turning heating off")
        nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
      }
    }
}

object NestService {
  type Result = EitherT[Future, UpstreamErrorResponse, Unit]
}
