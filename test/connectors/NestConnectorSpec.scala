package connectors

import play.api.inject.bind
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.Mockito.{ reset as mockReset, when }
import org.scalatest.concurrent.ScalaFutures

import com.github.tomakehurst.wiremock.client.WireMock.*

import cats.data.EitherT
import config.AppConfig
import models.nest.{ Connectivity, HvacStatus, ThermostatMode }
import testUtils.{ BaseSpec, WireMockHelper }

class NestConnectorSpec extends BaseSpec with WireMockHelper with ScalaFutures {

  lazy val mockAppConfig: AppConfig                   = mock[AppConfig]
  lazy val mockNestConnectorOAuth: NestConnectorOAuth =
    mock[NestConnectorOAuth]

  implicit override lazy val app = localGuiceApplicationBuilder()
    .overrides(
      bind[AppConfig].toInstance(mockAppConfig),
      bind[NestConnectorOAuth].toInstance(mockNestConnectorOAuth)
    )
    .build()

  lazy val sut: DefaultNestConnector =
    app.injector.instanceOf[DefaultNestConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()

    mockReset(mockAppConfig, mockNestConnectorOAuth)

    when(mockAppConfig.nestClientId)
      .thenReturn("test-client-id")

    when(mockAppConfig.nestClientSecret)
      .thenReturn("test-client-secret")

    when(mockAppConfig.nestProjectId)
      .thenReturn("test-project-id")

    when(mockAppConfig.nestDeviceId)
      .thenReturn("test-device-id")

    when(mockAppConfig.nestRedirectUri)
      .thenReturn("http://localhost:9234/nest/oauth/callback")

    when(mockAppConfig.nestApiHost)
      .thenReturn(s"http://localhost:${server.port}")

    when(mockAppConfig.googleTokenHost)
      .thenReturn(s"http://localhost:${server.port}")

    when(mockAppConfig.nestAuthHost)
      .thenReturn(s"http://localhost:${server.port}"): Unit
  }

  "getDevice" must {

    "return the Nest device" in {
      when(
        mockNestConnectorOAuth.getValidToken()
      ).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse]("access-token")
      )

      server.stubFor(
        get(
          urlEqualTo(
            "/v1/enterprises/test-project-id/devices/test-device-id"
          )
        )
          .withHeader("Authorization", equalTo("Bearer access-token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                {
                  "name": "enterprises/test-project-id/devices/test-device-id",
                  "type": "sdm.devices.types.THERMOSTAT",
                  "traits": {
                    "sdm.devices.traits.Connectivity": {
                      "status": "ONLINE"
                    },
                    "sdm.devices.traits.Temperature": {
                      "ambientTemperatureCelsius": 20.5
                    },
                    "sdm.devices.traits.Humidity": {
                      "ambientHumidityPercent": 45.0
                    },
                    "sdm.devices.traits.ThermostatMode": {
                      "mode": "HEAT"
                    },
                    "sdm.devices.traits.ThermostatEco": {
                      "mode": "OFF",
                      "heatCelsius": 26.0,
                      "coolCelsius": 16.0
                    },
                    "sdm.devices.traits.ThermostatTemperatureSetpoint": {
                      "heatCelsius": 21.0
                    },
                    "sdm.devices.traits.ThermostatHvac": {
                      "status": "OFF"
                    }
                  }
                }
                """
              )
          )
      )

      val result =
        await(
          sut
            .getDevice()
            .value
        )

      result match {
        case Right(device) =>
          device.name mustBe
            "enterprises/test-project-id/devices/test-device-id"

          device.deviceType mustBe
            "sdm.devices.types.THERMOSTAT"

          device.traits.connectivity mustBe Connectivity.ONLINE
          device.traits.temperatureCelsius mustBe 20.5
          device.traits.humidityPercent mustBe 45.0
          device.traits.thermostatMode mustBe ThermostatMode.HEAT
          device.traits.heatSetpointCelsius mustBe Some(21.0)
          device.traits.hvacStatus mustBe HvacStatus.OFF

        case Left(error) =>
          fail(s"Expected device but received $error")
      }
    }

    "return the upstream error when the Nest API returns 404" in {
      when(
        mockNestConnectorOAuth.getValidToken()
      ).thenReturn(
        EitherT.leftT[Future, String](UpstreamErrorResponse("Not found", 404))
      )

      server.stubFor(
        get(
          urlEqualTo(
            "/v1/enterprises/test-project-id/devices/test-device-id"
          )
        )
          .willReturn(
            aResponse()
              .withStatus(404)
              .withBody("Not found")
          )
      )

      val result =
        await(
          sut
            .getDevice()
            .value
        )

      result match {
        case Left(error) =>
          error.statusCode mustBe 404

        case Right(_) =>
          fail("Expected 404")
      }
    }
  }
}
