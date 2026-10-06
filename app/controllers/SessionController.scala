package controllers

import javax.inject.{ Inject, Singleton }
import play.api.data.Form
import play.api.http.HeaderNames
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }

import actions.AuthAction
import models.forms.UserDataForm
import models.LoggingWithRequest
import queries.SessionSqlQueries
import services.LoginService
import views.html.Login

@Singleton
class SessionController @Inject() (
    authAction: AuthAction,
    loginService: LoginService,
    sqlQueries: SessionSqlQueries,
    loginView: Login,
    val controllerComponents: ControllerComponents
)(
    implicit ec: ExecutionContext
) extends BaseController
    with I18nSupport
    with LoggingWithRequest {

  def loginOnLoad(redirectUrl: String): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    Future.successful(Ok(loginView(UserDataForm.userForm.fill(UserDataForm("", "", redirectUrl)))))
  }

  def loginOnSubmit: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val errorFunction: Form[UserDataForm] => Future[Result] = { (formWithErrors: Form[UserDataForm]) =>
      Future.successful(BadRequest(loginView(formWithErrors)))
    }

    val successFunction: UserDataForm => Future[Result] = { (userDataForm: UserDataForm) =>
      loginService
        .getUserData(userDataForm.username, userDataForm.password)
        .foldF(Future.successful(Redirect(routes.SessionController.loginOnLoad(userDataForm.redirectUrl)))) { result =>
          val returnUrl = new java.net.URI(
            Option(userDataForm.redirectUrl).filter(_.trim.nonEmpty).getOrElse(routes.HomeController.index().url)
          ).getPath
          val newLocalSession = authenticatedRequest.localSession
            .copy(sessionData = authenticatedRequest.localSession.sessionData.copy(userData = Some(result)))
          sqlQueries.updateSessionData(newLocalSession).map {
            case 1 => Redirect(returnUrl)
            case _ => InternalServerError("Could not update session data")
          }
        }
    }

    val formValidationResult = UserDataForm.userForm.bindFromRequest()
    formValidationResult.fold(errorFunction, successFunction)
  }

  def logoutOnLoad: Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    sqlQueries
      .removeSessionData(authenticatedRequest.localSession)
      .map { _ =>
        val returnUrl =
          new java.net.URI(
            authenticatedRequest.request.headers.get(HeaderNames.REFERER).getOrElse(routes.HomeController.index().url)
          ).getPath
        Redirect(returnUrl)
      }
      .recover {
        case ex =>
          logger.error(
            s"Failed to remove session ${authenticatedRequest.localSession.sessionId} during logout",
            ex
          )
          InternalServerError("Unable to log out")
      }
  }
}
