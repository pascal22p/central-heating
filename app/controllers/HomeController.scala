package controllers

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.Future

import actions.AuthAction

@Singleton
class HomeController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthAction
) extends BaseController
    with I18nSupport {
  def index(): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    Future.successful(Ok(""))
  }
}
