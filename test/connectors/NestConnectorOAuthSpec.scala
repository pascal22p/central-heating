package connectors

import play.api.inject.bind
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import play.api.Application
import scala.concurrent.Future

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ never, reset as mockReset, times, verify as mockVerify, when }
import org.scalatest.concurrent.ScalaFutures

import com.github.tomakehurst.wiremock.client.WireMock.*

import config.AppConfig
import queries.NestAuthorisationQueries
import testUtils.{ BaseSpec, WireMockHelper }

class NestConnectorOAuthSpec extends BaseSpec with WireMockHelper with ScalaFutures {

  lazy val mockAppConfig: AppConfig                               = mock[AppConfig]
  lazy val mockNestAuthorisationQueries: NestAuthorisationQueries =
    mock[NestAuthorisationQueries]

  implicit override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AppConfig].toInstance(mockAppConfig),
      bind[NestAuthorisationQueries].toInstance(mockNestAuthorisationQueries)
    )
    .build()

  lazy val sut: NestConnectorOAuth =
    app.injector.instanceOf[NestConnectorOAuth]

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

}
