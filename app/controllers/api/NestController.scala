package controllers.api

import javax.inject.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import connectors.NestConnector
import models.nest.HvacStatus.HEATING
import models.nest.ThermostatMode.OFF
import models.HeatingOverride
import queries.HeatingOverrideQueries
import services.HeatingControlService

@Singleton
class NestController @Inject() (
    val controllerComponents: ControllerComponents,
    nestConnector: NestConnector,
    heatingOverrideQueries: HeatingOverrideQueries,
    heatingControlService: HeatingControlService,
    authAction: AuthJourney
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def status(): Action[AnyContent] = authAction.apiAccess.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    nestConnector.getDevice().value.map {
      case Right(device) =>
        val result = if (device.traits.hvacStatus == HEATING) { 1 }
        else { 0 }
        Ok(s"$result")

      case Left(error) =>
        InternalServerError(
          s"Unable to read Nest: ${error.message}"
        )
    }
  }

  def on(): Action[AnyContent] = authAction.apiAccess.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    nestConnector.getDevice().value.flatMap {
      case Right(device) =>
        if (device.traits.thermostatMode == OFF) {
          Future.successful(BadRequest("Thermostat in Summer mode"))
        } else {
          for {
            _ <- heatingOverrideQueries.setOverride(HeatingOverride.Heat, Some(60))
            _ <- heatingControlService.applyOverride(HeatingOverride.Heat)
          } yield NoContent
        }

      case Left(error) =>
        Future.successful(
          InternalServerError(
            s"Unable to read Nest: ${error.message}"
          )
        )
    }
  }

  def off(): Action[AnyContent] = authAction.apiAccess.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    nestConnector.getDevice().value.flatMap {
      case Right(device) =>
        if (device.traits.thermostatMode == OFF) {
          Future.successful(BadRequest("Thermostat in Summer mode"))
        } else {
          for {
            _ <- heatingOverrideQueries.setOverride(HeatingOverride.Idle, Some(60))
            _ <- heatingControlService.applyOverride(HeatingOverride.Idle)
          } yield NoContent
        }

      case Left(error) =>
        Future.successful(
          InternalServerError(
            s"Unable to read Nest: ${error.message}"
          )
        )
    }
  }
}
