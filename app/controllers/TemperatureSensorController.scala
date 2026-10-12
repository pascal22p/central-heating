package controllers

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.ExecutionContext

import actions.AuthJourney
import services.DevicesService
import views.html.SensorsView

class TemperatureSensorController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    devicesService: DevicesService,
    sensorsView: SensorsView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    devicesService.getTemperatureSensors.map { sensors =>
      Ok(sensorsView(sensors))
    }
  }
}
