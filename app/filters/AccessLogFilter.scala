package filters

import java.time.Instant
import javax.inject.Inject
import play.api.libs.json.*
import play.api.mvc.*
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }

import org.apache.pekko.stream.Materializer

class AccessLogFilter @Inject() (
    implicit val mat: Materializer,
    ec: ExecutionContext
) extends Filter
    with Logging {

  private def optional(value: Option[String]): JsValue =
    value.map(JsString.apply).getOrElse(JsNull)

  override def apply(
      nextFilter: RequestHeader => Future[Result]
  )(request: RequestHeader): Future[Result] = {

    val start     = System.nanoTime()
    val timestamp = Instant.now()

    nextFilter(request).map { result =>
      val durationMs =
        (System.nanoTime() - start) / 1_000_000.0

      val responseSize =
        result.body.contentLength

      val json = Json.obj(
        "type"           -> "access",
        "remote_address" -> request.remoteAddress,
        "timestamp"      -> timestamp.toString,
        "method"         -> request.method,
        "uri"            -> request.uri,
        "protocol"       -> request.version,
        "status"         -> result.header.status,
        "response_size"  -> responseSize.map(JsNumber(_)).getOrElse(JsNull),
        "referer"        -> optional(request.headers.get("Referer")),
        "user_agent"     -> optional(request.headers.get("User-Agent")),
        "duration_ms"    -> JsNumber(BigDecimal(durationMs).setScale(3, BigDecimal.RoundingMode.HALF_UP)),
        "request_id"     -> optional(
          request.headers.get("X-Request-Id")
        ),
        "session_id" -> optional(
          request.headers.get("X-Session-Id")
        )
      )

      logger.info(Json.stringify(json))

      result
    }
  }
}
