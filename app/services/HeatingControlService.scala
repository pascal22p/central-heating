package services

import javax.inject.{ Inject, Singleton }
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ RunningState, Trv }

@Singleton
class HeatingControlService @Inject() (
    appConfig: AppConfig,
    nestConnector: NestConnector,
    trvService: TrvService
)(implicit ec: ExecutionContext)
    extends Logging {

  def onTrvUpdate(trv: Trv): Future[Unit] =
    if (!appConfig.heatingControlIsEnabled) {
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
            logger.info("Success")
            ()
          case Left(error) => logger.error(s"Failed to update heating for ${trv.name}: ${error.message}")
        }
        .recover {
          case NonFatal(e) => logger.error(s"Unexpected error updating heating for ${trv.name}", e)
        }
    }

  private def turnOnIfOff(trv: Trv)(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] = {
    nestConnector.getDevice().flatMap { device =>
      if (device.traits.thermostatMode == "HEAT" && device.traits.hvacStatus == "OFF") {
        logger.info(s"Heat requested by ${trv.name} and heating is off, turning heating on")
        nestConnector.setTemperature(appConfig.heatingDemandActiveTemperatureCelsius)
      } else {
        if (device.traits.thermostatMode != "HEAT") {
          logger.info("The thermostat is OFF on summer mode. Heating cannot be turned on.")
        }
        EitherT.rightT[Future, UpstreamErrorResponse](())
      }
    }
  }

  private def turnOffIfAllIdle(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] =
    EitherT.liftF(trvService.getTrvs).flatMap { trvs =>
      if (trvs.forall(_.runningState == RunningState.Idle)) {
        nestConnector.getDevice().flatMap { device =>
          if (device.traits.thermostatMode == "HEAT" && device.traits.hvacStatus == "HEATING") {
            logger.info(s"All TRVs are requesting idle and heating is on, turning heating off")
            nestConnector.setTemperature(appConfig.heatingDemandStandbyTemperatureCelsius)
          } else {
            if (device.traits.thermostatMode != "HEAT") {
              logger.info("The thermostat is OFF on summer mode.")
            }
            EitherT.rightT(())
          }
        }
      } else EitherT.rightT(())
    }

}
