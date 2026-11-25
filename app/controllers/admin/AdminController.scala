package controllers.admin

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.*

import actions.AuthJourney
import views.html.admin.AdminHomeView

@Singleton
final class AdminController @Inject() (
    authJourney: AuthJourney,
    val controllerComponents: ControllerComponents,
    adminHomeView: AdminHomeView
) extends BaseController
    with I18nSupport {

  def index: Action[AnyContent] =
    authJourney.authWithAdminRight { implicit request =>
      Ok(adminHomeView())
    }
}
