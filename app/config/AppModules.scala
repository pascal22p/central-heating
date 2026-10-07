package config

import play.api.{ Configuration, Environment }
import play.api.inject.{ Binding, Module }

import uk.gov.hmrc.http.client.HttpClientV2

import services.MqttService

class AppModules extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Seq(
      bind[HttpClientV2].toProvider[HttpClientV2Provider],
      bind[MqttService].toSelf.eagerly()
    )
  }
}
