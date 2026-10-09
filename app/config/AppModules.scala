package config

import play.api.{ Configuration, Environment, Logging }
import play.api.inject.{ Binding, Module }

import uk.gov.hmrc.http.client.HttpClientV2

import services.MqttSubscriberService

class AppModules extends Module with Logging {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    if (configuration.get[Boolean]("microservice.services.mqtt.isEnabled")) {
      logger.info("MqttSubscriberService is enabled via configuration")
      Seq(
        bind[HttpClientV2].toProvider[HttpClientV2Provider],
        bind[MqttSubscriberService].toSelf.eagerly()
      )
    } else {
      logger.info("MqttSubscriberService is disabled via configuration")
      Seq(
        bind[HttpClientV2].toProvider[HttpClientV2Provider]
      )
    }
  }
}
