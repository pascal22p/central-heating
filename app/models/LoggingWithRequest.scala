package models

import play.api.{ Logging, MarkerContext }
import play.api.mvc.RequestHeader

import org.slf4j.{ MDC, MarkerFactory }

import models.Attrs

trait LoggingWithRequest extends Logging {

  @SuppressWarnings(Array("org.wartremover.warts.RedundantConversions"))
  implicit def requestHeaderToMarkerContext(implicit request: RequestHeader): MarkerContext = {
    val requestId = request.attrs.get(Attrs.RequestId).fold("Unknown")(_.toString)
    val sessionId = request.attrs.get(Attrs.SessionId).fold("Unknown")(_.toString)

    MDC.put("request_id", requestId)
    MDC.put("session_id", sessionId)

    val marker = MarkerFactory.getDetachedMarker(s"requestId=$requestId, sessionId=$sessionId")
    MarkerContext(marker)
  }

  def withRequestLogging[A](block: => A)(implicit request: RequestHeader): A = {
    requestHeaderToMarkerContext
    try block
    finally {
      MDC.remove("request_id")
      MDC.remove("session_id")
    }
  }

}
