package controllers

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.Future

import actions.AuthJourney
import services.MqttService
import views.html.TrvsView

class TrvController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    mqttService: MqttService,
    trvsView: TrvsView
) extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    Future.successful(Ok(trvsView(mqttService.getTrvs)))
  }
}
