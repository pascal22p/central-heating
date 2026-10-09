package controllers

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.ExecutionContext

import actions.AuthJourney
import services.TrvService
import views.html.TrvsView

class TrvController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    trvService: TrvService,
    trvsView: TrvsView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    trvService.getTrvs.map { trvs =>
      Ok(trvsView(trvs))
    }
  }
}
