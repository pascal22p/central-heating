package config

import javax.inject.Inject
import play.api.Configuration

class AppConfig @Inject() (configuration: Configuration) {
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
}
