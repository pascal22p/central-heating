package jobs

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.DurationInt

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.apache.pekko.actor.Actor

import cats.data.EitherT
import config.AppConfig
import connectors.{ GraphiteConnector, NestConnector }
import io.opentelemetry.api.trace.{ SpanKind, StatusCode }
import io.opentelemetry.api.GlobalOpenTelemetry
import models.LoggingWithRequest

class NestMetricsToGraphiteJob(
    nestConnector: NestConnector,
    graphiteConnector: GraphiteConnector,
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Actor
    with LoggingWithRequest {

  private val tracer = GlobalOpenTelemetry.getTracer(appConfig.appName)

  def receive: Receive = {
    case RunJob => {
      logger.info("\u001b[35m Starting scheduled job nest metrics to graphite\u001b[0m")
      val span = tracer
        .spanBuilder("NestMetricsToGraphiteJob")
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan()
      val scope = span.makeCurrent()

      implicit val hc: HeaderCarrier = HeaderCarrier()

      try {
        Await.result(
          nestConnector
            .getDevice()
            .flatMap { device =>
              logger.info(s"Nest device metrics: $device")
              span.setAttribute("job.status", "Received Nest metrics successfully")

              EitherT.liftF[Future, UpstreamErrorResponse, Unit](
                graphiteConnector
                  .send(device)
                  .map { _ =>
                    logger.info(s"Nest metrics sent to graphite successfully")
                    span.setAttribute("job.status", "success")
                    span.setStatus(StatusCode.OK)
                    ()
                  }
                  .recover {
                    case e: Exception =>
                      span.recordException(e)
                      span.setStatus(StatusCode.ERROR, e.getMessage)
                      logger.error(s"Error sending nest metrics to graphite: ${e.getMessage}", e)
                      ()
                  }
              )
            }
            .value,
          appConfig.nestSchedulerTimeoutInSeconds.seconds
        ) match {
          case Right(_) =>
            ()

          case Left(error) =>
            throw error
        }
      } catch {
        case e: Exception =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR, e.getMessage)
          throw e
      } finally {
        scope.close()
        span.end()
      }
    }
  }
}
