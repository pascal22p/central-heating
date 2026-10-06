package controllers.admin

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.Logging
import scala.concurrent.Future

import actions.AuthJourney

@Singleton
class AdminController @Inject() (
    authJourney: AuthJourney,
    val controllerComponents: ControllerComponents
) extends BaseController
    with I18nSupport
    with Logging {

  def index: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    Future.successful(Ok(""))
  }

}
