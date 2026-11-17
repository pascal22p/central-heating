package controllers

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import connectors.NestConnector
import models.mqtt.TemperatureSensor
import queries.HeatingOverrideQueries
import services.DevicesService
import views.html.DashboardView

@Singleton
class HomeController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    nestConnector: NestConnector,
    devicesService: DevicesService,
    heatingOverrideQueries: HeatingOverrideQueries,
    dashboardView: DashboardView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    given HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)

    for {
      nestResult     <- nestConnector.getDevice().value
      sensors        <- devicesService.getTemperatureSensors
      trvs           <- devicesService.getTrvs
      sensorStatuses <- Future.sequence(sensors.map(sensorStatus))
      manualOverride <- heatingOverrideQueries.getOverrideState
    } yield {
      nestResult match {
        case Right(device) =>
          Ok(dashboardView(device, sensorStatuses, trvs, manualOverride))

        case Left(error) =>
          InternalServerError(s"Unable to read Nest: ${error.message}")
      }
    }
  }

  private def sensorStatus(
      sensor: TemperatureSensor
  )(implicit hc: HeaderCarrier): Future[(TemperatureSensor, Double)] =
    devicesService.targetTemperature(sensor.name, 1).map { target =>
      sensor -> target
    }
}
