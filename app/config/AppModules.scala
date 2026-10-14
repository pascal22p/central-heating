package config

import java.time.{ Clock, ZoneId }
import play.api.{ Configuration, Environment, Logging }
import play.api.inject.{ Binding, Module }

import uk.gov.hmrc.http.client.HttpClientV2

import connectors.{ CachingNestConnector, DefaultNestConnector, NestConnector }
import jobs.JobNestScheduler
import services.MqttSubscriberService

class AppModules extends Module with Logging {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    val nestScheduler = if (configuration.get[Boolean]("scheduler.nest.isEnabled")) {
      Seq(bind[JobNestScheduler].toSelf.eagerly())
    } else {
      logger.warn("Nest scheduler is disabled via configuration")
      Seq.empty
    }

    val mqttSubscriber = if (configuration.get[Boolean]("microservice.services.mqtt.isEnabled")) {
      logger.info("Mqtt subscriber is enabled via configuration")
      Seq(bind[MqttSubscriberService].toSelf.eagerly())
    } else {
      logger.warn("Mqtt subscriber is disabled via configuration")
      Seq.empty
    }

    Seq(
      bind[HttpClientV2].toProvider[HttpClientV2Provider],
      bind[Clock]
        .toInstance(Clock.system(ZoneId.of("Europe/London"))),
      bind[NestConnector].qualifiedWith("default").to[DefaultNestConnector],
      bind[NestConnector]
        .to[CachingNestConnector],

    ) ++ nestScheduler ++ mqttSubscriber
  }
}
