package actions

import java.time.LocalDateTime
import javax.inject.Inject
import play.api.mvc.*
import play.api.MarkerContext
import scala.concurrent.{ ExecutionContext, Future }

import com.google.inject.ImplementedBy

import models.{ Attrs, AuthenticatedRequest, LoggingWithRequest, Session, SessionData }
import queries.SessionSqlQueries

class AuthActionImpl @Inject() (
    sqlQueries: SessionSqlQueries,
    cc: MessagesControllerComponents,
)(implicit val ec: ExecutionContext)
    extends AuthAction
    with LoggingWithRequest {

  override val parser: BodyParser[AnyContent]               = cc.parsers.defaultBodyParser
  protected override val executionContext: ExecutionContext = cc.executionContext

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    val sessionId =
      request.attrs
        .get(Attrs.SessionId)
        .getOrElse {
          throw new IllegalStateException("Session ID missing from request")
        }

    logger.info(s"AuthAction with session ID: $sessionId")

    sqlQueries.getSessionData(sessionId).flatMap {
      case Some(session) =>
        sqlQueries.sessionKeepAlive(sessionId).flatMap { _ =>
          block {
            AuthenticatedRequest(
              request,
              session
            )
          }
        }
      case None =>
        val session = Session(sessionId, SessionData(None), LocalDateTime.now())
        sqlQueries.putSessionData(session).flatMap { _ =>
          block {
            AuthenticatedRequest(
              request,
              session
            )
          }
        }
    }
  }

}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction
    extends ActionBuilder[AuthenticatedRequest, AnyContent]
    with ActionFunction[Request, AuthenticatedRequest]
