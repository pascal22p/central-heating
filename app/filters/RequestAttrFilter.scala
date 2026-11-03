package filters

import java.util.UUID
import javax.inject.Inject
import play.api.mvc.*
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

import uk.gov.hmrc.http.HeaderNames

import org.apache.pekko.stream.Materializer

import config.ErrorHandler
import models.Attrs

class RequestAttrFilter @Inject() (
    errorHandler: ErrorHandler,
    implicit val mat: Materializer
)(implicit ec: ExecutionContext)
    extends Filter
    with Logging {

  override def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    val requestId = request.headers.get(HeaderNames.xRequestId).getOrElse(UUID.randomUUID().toString)

    val sessionId =
      request.attrs
        .get(Attrs.SessionId)
        .orElse(request.session.get("sessionId"))
        .getOrElse {
          throw new IllegalStateException("Session ID missing from request")
        }

    // Attach requestId and sessionId to request attributes
    val enrichedRequest = request
      .addAttr(Attrs.RequestId, requestId)
      .addAttr(Attrs.SessionId, sessionId)
      .withHeaders(
        request.headers
          .remove(HeaderNames.xRequestId)
          .add(HeaderNames.xRequestId -> requestId)
          .remove(HeaderNames.xSessionId)
          .add(HeaderNames.xSessionId -> sessionId)
      )

    logger.info(
      s"RequestAttrFilter assigned sessionId=`$sessionId` requestId=`$requestId` to ${request.method} ${request.uri}"
    )

    next(enrichedRequest)
      .map { result =>
        result
          .withHeaders(
            result.header.headers
              .filterNot(_._1.equalsIgnoreCase(HeaderNames.xRequestId))
              .filterNot(_._1.equalsIgnoreCase(HeaderNames.xSessionId))
              .toSeq ++ Seq(
              HeaderNames.xRequestId -> requestId
            )*
          )
          .discardingHeader(HeaderNames.xSessionId)
      }
      .recoverWith {
        case NonFatal(e) =>
          errorHandler.onServerError(enrichedRequest, e)
      }
  }
}
