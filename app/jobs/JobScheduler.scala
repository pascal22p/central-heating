package jobs

import javax.inject.{ Inject, Singleton }
import play.api.inject.ApplicationLifecycle
import scala.concurrent.duration.*

import org.apache.pekko.actor.{ ActorRef, ActorSystem, Props }

import config.AppConfig
import connectors.{ GraphiteConnector, NestConnector }
import models.LoggingWithRequest

@Singleton
class JobScheduler @Inject() (
    actorSystem: ActorSystem,
    lifecycle: ApplicationLifecycle,
    nestConnector: NestConnector,
    graphiteConnector: GraphiteConnector,
    appConfig: AppConfig
) extends LoggingWithRequest {

  import actorSystem.dispatcher

  logger.info("Registering partial update job scheduler")
  private val nestUpdateActorRef: ActorRef =
    actorSystem.actorOf(
      Props(new NestMetricsToGraphiteJob(nestConnector, graphiteConnector, appConfig)),
      "partial-update-actor"
    )

  private val partialUpdateCancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(
      initialDelay = appConfig.nestSchedulerStartDelayInSeconds.seconds,
      delay = appConfig.nestSchedulerIntervalInSeconds.seconds,
      receiver = nestUpdateActorRef,
      message = RunJob
    )

  // Stop scheduler when app shuts down
  lifecycle.addStopHook { () =>
    partialUpdateCancellable.cancel()
    actorSystem.stop(nestUpdateActorRef)
    scala.concurrent.Future.successful(())
  }
}
