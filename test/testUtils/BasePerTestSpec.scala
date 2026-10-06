package testUtils

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import org.scalatestplus.play.PlaySpec

trait BasePerTestSpec
    extends PlaySpec
    with GuiceOneAppPerTest
    with ScalaFutures
    with Injecting
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
}
