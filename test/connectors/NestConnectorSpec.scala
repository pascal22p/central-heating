package connectors

import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ never, reset => mockReset, times, verify => mockVerify, when }
import org.scalatest.concurrent.ScalaFutures

import com.github.tomakehurst.wiremock.client.WireMock.*

import cats.data.OptionT
import config.AppConfig
import queries.NestAuthorisationQueries
import testUtils.{ BaseSpec, WireMockHelper }

class NestConnectorSpec extends BaseSpec with WireMockHelper with ScalaFutures {

  lazy val mockAppConfig: AppConfig                               = mock[AppConfig]
  lazy val mockNestAuthorisationQueries: NestAuthorisationQueries =
    mock[NestAuthorisationQueries]

  protected override def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[AppConfig].toInstance(mockAppConfig),
        bind[NestAuthorisationQueries].toInstance(mockNestAuthorisationQueries)
      )

  lazy val sut: NestConnector =
    app.injector.instanceOf[NestConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()

    sut.clearToken()

    mockReset(mockAppConfig, mockNestAuthorisationQueries)

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

  "authorizationUrl" must {

    "return the Google authorization URL with all required parameters" in {
      val result = sut.authorizationUrl()

      result must startWith(
        s"http://localhost:${server.port}/partnerconnections/test-project-id/auth?"
      )

      result must include("redirect_uri=http%3A%2F%2Flocalhost%3A9234%2Fnest%2Foauth%2Fcallback")
      result must include("client_id=test-client-id")
      result must include("access_type=offline")
      result must include("prompt=consent")
      result must include("response_type=code")
      result must include("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fsdm.service")
      result must include("state=")
    }
  }

  "handleCallback" must {

    "reject an invalid OAuth state" in {
      val result =
        await(
          sut
            .handleCallback(
              code = "test-code",
              state = "incorrect-state"
            )
            .value
        )

      result match {
        case Left(error) =>
          error.statusCode mustBe 400
          error.message mustBe "Invalid OAuth state"

        case Right(_) =>
          fail("Expected OAuth callback to fail")
      }
    }

    "exchange the authorization code and persist the refresh token" in {
      val authorizationUrl = sut.authorizationUrl()

      val state =
        authorizationUrl
          .split("state=")
          .last

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "access_token": "access-token",
                    "refresh_token": "refresh-token",
                    "expires_in": 3600
                  }
                """
              )
          )
      )

      when(
        mockNestAuthorisationQueries.saveRefreshToken("refresh-token")
      ).thenReturn(Future.successful(()))

      val result =
        await(
          sut
            .handleCallback(
              code = "test-code",
              state = state
            )
            .value
        )

      result mustBe Right(())

      mockVerify(
        mockNestAuthorisationQueries,
        times(1)
      ).saveRefreshToken("refresh-token")

      mockVerify(
        mockNestAuthorisationQueries,
        never()
      ).clearRefreshToken

      server.verify(
        postRequestedFor(urlEqualTo("/token"))
          .withRequestBody(containing("code=test-code"))
      )
    }

    "reject a failed authorization-code exchange" in {
      val authorizationUrl = sut.authorizationUrl()

      val state =
        authorizationUrl
          .split("state=")
          .last

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(400)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "error": "invalid_grant"
                  }
                """
              )
          )
      )

      val result =
        await(
          sut
            .handleCallback(
              code = "bad-code",
              state = state
            )
            .value
        )

      result.isLeft mustBe true

      mockVerify(
        mockNestAuthorisationQueries,
        never()
      ).saveRefreshToken(any[String])
    }
  }

  "getDevice" must {

    "return the Nest device" in {
      when(
        mockNestAuthorisationQueries.getRefreshToken
      ).thenReturn(
        OptionT.some[Future]("refresh-token")
      )

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "access_token": "access-token",
                    "expires_in": 3600
                  }
                """
              )
          )
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

          device.traits.connectivity mustBe "ONLINE"
          device.traits.temperatureCelsius mustBe 20.5
          device.traits.humidityPercent mustBe 45.0
          device.traits.thermostatMode mustBe "HEAT"
          device.traits.heatSetpointCelsius mustBe Some(21.0)
          device.traits.hvacStatus mustBe "OFF"

        case Left(error) =>
          fail(s"Expected device but received $error")
      }
    }

    "return an error when Nest is not authorised" in {
      when(
        mockNestAuthorisationQueries.getRefreshToken
      ).thenReturn(
        OptionT.none[Future, String]
      )

      val result =
        await(
          sut
            .getDevice()
            .value
        )

      result match {
        case Left(error) =>
          error.statusCode mustBe 401
          error.message mustBe "Nest authorisation required"

        case Right(_) =>
          fail("Expected unauthorised result")
      }

      mockVerify(
        mockNestAuthorisationQueries,
        times(1)
      ).getRefreshToken
    }

    "refresh the access token when the existing access token has expired" in {
      when(
        mockNestAuthorisationQueries.getRefreshToken
      ).thenReturn(
        OptionT.some[Future]("refresh-token")
      )

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "access_token": "new-access-token",
                    "expires_in": 3600
                  }
                """
              )
          )
      )

      server.stubFor(
        get(
          urlEqualTo(
            "/v1/enterprises/test-project-id/devices/test-device-id"
          )
        )
          .withHeader(
            "Authorization",
            equalTo("Bearer new-access-token")
          )
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

      result.isRight mustBe true

      mockVerify(
        mockNestAuthorisationQueries,
        times(1)
      ).getRefreshToken
    }

    "return the upstream error when the Nest API returns 404" in {
      when(
        mockNestAuthorisationQueries.getRefreshToken
      ).thenReturn(
        OptionT.some[Future]("refresh-token")
      )

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "access_token": "access-token",
                    "expires_in": 3600
                  }
                """
              )
          )
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

    "return the upstream error when Nest returns 403" in {
      when(
        mockNestAuthorisationQueries.getRefreshToken
      ).thenReturn(
        OptionT.some[Future]("refresh-token")
      )

      server.stubFor(
        post(urlEqualTo("/token"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """
                  {
                    "access_token": "access-token",
                    "expires_in": 3600
                  }
                """
              )
          )
      )

      server.stubFor(
        get(
          urlEqualTo(
            "/v1/enterprises/test-project-id/devices/test-device-id"
          )
        )
          .willReturn(
            aResponse()
              .withStatus(403)
              .withBody("Forbidden")
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
          error.statusCode mustBe 403

        case Right(_) =>
          fail("Expected 403")
      }
    }
  }
}
