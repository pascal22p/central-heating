package testUtils

import java.time.LocalDateTime
import play.api.mvc.{ AnyContent, BodyParser, Request, Result }
import scala.concurrent.{ ExecutionContext, Future }

import actions.AuthAction
import models.{ AuthenticatedRequest, Session, SessionData }

class FakeAuthAction extends AuthAction {
  def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    block(AuthenticatedRequest(request, Session("sessionId", SessionData(), LocalDateTime.now)))
  }

  protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
}
