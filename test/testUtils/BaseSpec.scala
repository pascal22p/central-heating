package testUtils

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.Application

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.play.PlaySpec

trait BaseSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with ScalaFutures
    with Injecting
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.mqtt.isEnabled"      -> false,
        "scheduler.nest.isEnabled"                  -> false,
        "scheduler.nest.startDelayInSeconds"        -> 2000,
        "scheduler.nest.schedulerIntervalInSeconds" -> 2000
      )

  implicit override lazy val app: Application = localGuiceApplicationBuilder().build()

}
