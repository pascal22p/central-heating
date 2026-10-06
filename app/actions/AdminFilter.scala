package actions

import javax.inject.{ Inject, Singleton }
import play.api.i18n.{ I18nSupport, MessagesApi }
import play.api.mvc.{ ActionFilter, ControllerComponents, Result }
import play.api.mvc.Results.{ Forbidden, Redirect }
import scala.concurrent.{ ExecutionContext, Future }

import models.AuthenticatedRequest
import views.html.admin.ForbiddenView

@Singleton
class AdminFilter @Inject() (
    cc: ControllerComponents,
    forbiddenView: ForbiddenView
) extends ActionFilter[AuthenticatedRequest]
    with I18nSupport {

  override def messagesApi: MessagesApi = cc.messagesApi

  // scalastyle:off cyclomatic.complexity
  override def filter[A](request: AuthenticatedRequest[A]): Future[Option[Result]] = Future {
    val messages = request2Messages(using request)
    request.localSession.sessionData.userData.map(_.isAdmin) match {
      // not authenticated
      case None => Some(Redirect(controllers.routes.SessionController.loginOnLoad(request.uri)))
      // authenticated but not an admin
      case Some(false) => Some(Forbidden(forbiddenView()(using request, messages)))
      // authenticate and an admin
      case Some(true) => None
    }
  }

  protected implicit override val executionContext: ExecutionContext = cc.executionContext

}
