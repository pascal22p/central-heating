package config

import javax.inject.Inject
import play.api.Configuration
import scala.annotation.unused

class AppConfig @Inject() (configuration: Configuration) {
  @unused
  private def baseUrl(serviceName: String): String = {
    val servicesRoot = "microservice.services"
    val protocol     = configuration.getOptional[String](s"$servicesRoot.$serviceName.protocol").getOrElse("http")
    val host         = configuration.get[String](s"$servicesRoot.$serviceName.host")
    val port         = configuration.get[Int](s"$servicesRoot.$serviceName.port")
    s"$protocol://$host:$port"
  }

  val appName: String    = configuration.get[String]("appName")
  val commitHash: String = sys.props.getOrElse("git.commit.hash", "unknown")

  lazy val nestClientId: String =
    configuration.get[String]("microservice.services.nest.client-id")

  lazy val nestClientSecret: String =
    configuration.get[String]("microservice.services.nest.client-secret")

  lazy val nestProjectId: String =
    configuration.get[String]("microservice.services.nest.project-id")

  lazy val nestDeviceId: String =
    configuration.get[String]("microservice.services.nest.device-id")

  lazy val nestRedirectUri: String =
    configuration.get[String]("microservice.services.nest.redirect-uri")

  lazy val nestApiHost: String =
    configuration.get[String]("microservice.services.nest.api-host")

  lazy val googleTokenHost: String =
    configuration.get[String]("microservice.services.nest.google-token-host")

  lazy val nestAuthHost: String =
    configuration.get[String]("microservice.services.nest.auth-host")

  lazy val graphiteHost: String =
    configuration.get[String]("microservice.services.graphite.host")

  lazy val graphitePort: Int =
    configuration.get[Int]("microservice.services.graphite.port")

  lazy val graphitePrefix: String =
    configuration.get[String]("microservice.services.graphite.prefix")

  lazy val graphiteConnectTimeoutMs: Int =
    configuration.get[Int](
      "microservice.services.graphite.connect-timeout-ms"
    )

  lazy val graphiteReadTimeoutMs: Int =
    configuration.get[Int](
      "microservice.services.graphite.read-timeout-ms"
    )

  lazy val mqttBrokerUrl: String =
    configuration.get[String](
      "microservice.services.mqtt.broker-url"
    )

  lazy val mqttClientId: String =
    configuration.get[String](
      "microservice.services.mqtt.client-id"
    )

  lazy val mqttIsEnabled: Boolean =
    configuration.get[Boolean](
      "microservice.services.mqtt.isEnabled"
    )

  lazy val nestSchedulerIsEnabled: Boolean =
    configuration.get[Boolean](
      "scheduler.nest.isEnabled"
    )

  lazy val nestSchedulerStartDelayInSeconds: Int =
    configuration.get[Int](
      "scheduler.nest.startDelayInSeconds"
    )

  lazy val nestSchedulerIntervalInSeconds: Int =
    configuration.get[Int](
      "scheduler.nest.intervalInSeconds"
    )

  lazy val nestSchedulerTimeoutInSeconds: Int =
    configuration.get[Int](
      "scheduler.nest.timeoutInSeconds"
    )

  lazy val trvDevices: Seq[String] =
    configuration.get[String](
      "microservice.services.mqtt.trv-devices"
    ).split(",")

  lazy val heatingDemandActiveTemperatureCelsius: Double =
    configuration.get[Double]("heating-demand.active-temperature-celsius")

  lazy val heatingDemandStandbyTemperatureCelsius: Double =
    configuration.get[Double]("heating-demand.standby-temperature-celsius")

  lazy val heatingControlIsEnabled: Boolean =
    configuration.get[Boolean]("heating-demand.isEnabled")
}
