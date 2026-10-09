package controllers

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.{ ExecutionContext, Future }

import actions.AuthJourney
import queries.TrvQueries
import views.html.TrvsView

class TrvController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    trvQueries: TrvQueries,
    trvsView: TrvsView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    trvQueries.getTrvs.map { trvs =>
      Ok(trvsView(trvs))
    }
  }
}
