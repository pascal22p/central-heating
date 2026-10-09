package config

import play.api.{ Configuration, Environment, Logging }
import play.api.inject.{ Binding, Module }

import jobs.JobNestScheduler

class JobNestSchedulerModule extends Module with Logging {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    logger.info("JobSchedulerModule bindings")
    if (configuration.get[Boolean]("scheduler.nest.isEnabled")) {
      Seq(
        bind[JobNestScheduler].toSelf.eagerly()
      )
    } else {
      logger.info("JobSchedulerModule is disabled via configuration")
      Seq.empty
    }
  }
}
