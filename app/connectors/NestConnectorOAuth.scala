package connectors

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.*
import play.api.libs.json.*
import play.api.libs.ws.WSBodyWritables.writeableOf_urlEncodedForm
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.{ HeaderCarrier, HttpReads, HttpResponse, StringContextOps, UpstreamErrorResponse }
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*

import cats.data.EitherT
import config.AppConfig
import models.nest.{ NestToken, NestTokenResponse }
import queries.NestAuthorisationQueries

@Singleton
class NestConnectorOAuth @Inject() (
    appConfig: AppConfig,
    http: HttpClientV2,
    nestAuthorisationQueries: NestAuthorisationQueries
)(implicit ec: ExecutionContext)
    extends Logging {

  private val scope =
    "https://www.googleapis.com/auth/sdm.service"

  @volatile private var token: Option[NestToken] = None

  @volatile private var oauthState: Option[String] = None

  def clearToken(): Unit =
    token = None

  private def encode(value: String): String =
    URLEncoder
      .encode(value, StandardCharsets.UTF_8)
      .replace("+", "%20")

  // ---------------------------------------------------------------------------
  // OAuth
  // ---------------------------------------------------------------------------

  def authorizationUrl(): String = {

    val state = java.util.UUID.randomUUID().toString

    oauthState = Some(state)

    val params = Seq(
      "redirect_uri"  -> appConfig.nestRedirectUri,
      "client_id"     -> appConfig.nestClientId,
      "access_type"   -> "offline",
      "prompt"        -> "consent",
      "response_type" -> "code",
      "scope"         -> scope,
      "state"         -> state
    )

    val query = params
      .map {
        case (key, value) =>
          s"${encode(key)}=${encode(value)}"
      }
      .mkString("&")

    s"${appConfig.nestAuthHost}/partnerconnections/${appConfig.nestProjectId}/auth?$query"
  }

  def handleCallback(code: String, state: String)(
      implicit hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Unit] = {
    oauthState match {
      case Some(expectedState) if expectedState == state =>
        exchangeAuthorizationCode(code)

      case _ =>
        EitherT.leftT(
          UpstreamErrorResponse(
            "Invalid OAuth state",
            400,
            400
          )
        )
    }
  }

  def exchangeAuthorizationCode(
      code: String
  )(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] = {
    val formData: Map[String, Seq[String]] = Map(
      "code"          -> Seq(code),
      "client_id"     -> Seq(appConfig.nestClientId),
      "client_secret" -> Seq(appConfig.nestClientSecret),
      "redirect_uri"  -> Seq(appConfig.nestRedirectUri),
      "grant_type"    -> Seq("authorization_code")
    )

    EitherT(
      http
        .post(url"${appConfig.googleTokenHost}/token")
        .withBody(formData)
        .execute[Either[UpstreamErrorResponse, HttpResponse]]
    )
      .flatMap { response =>
        val json = response.json

        val accessToken =
          (json \ "access_token").as[String]

        val refreshToken =
          (json \ "refresh_token").as[String]

        val expiresIn =
          (json \ "expires_in").as[Long]

        val newToken =
          NestToken(
            accessToken = accessToken,
            expiresAt = System.currentTimeMillis() + expiresIn * 1000
          )

        EitherT(
          nestAuthorisationQueries
            .saveRefreshToken(refreshToken)
            .map { _ =>
              token = Some(newToken)
              oauthState = None

              Right(())
            }
        )
      }
  }

  // ---------------------------------------------------------------------------
  // Access token
  // ---------------------------------------------------------------------------

  def getValidToken()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, String] = {

    token match {
      case Some(currentToken) if !currentToken.isExpired =>
        EitherT.rightT(currentToken.accessToken)
      case _ =>
        refreshAccessToken()
    }
  }

  def refreshAccessToken()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, String] = {
    nestAuthorisationQueries.getRefreshToken
      .toRight[UpstreamErrorResponse](
        UpstreamErrorResponse("Nest authorisation required", 401, 401)
      )
      .flatMap(refreshToken => refreshAccessToken(refreshToken))
  }

  def refreshAccessToken(
      refreshToken: String
  )(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, String] = {
    val formData: Map[String, Seq[String]] = Map(
      "client_id"     -> Seq(appConfig.nestClientId),
      "client_secret" -> Seq(appConfig.nestClientSecret),
      "refresh_token" -> Seq(refreshToken),
      "grant_type"    -> Seq("refresh_token")
    )

    EitherT(
      http
        .post(url"${appConfig.googleTokenHost}/token")
        .withBody(formData)
        .execute[Either[UpstreamErrorResponse, NestTokenResponse]]
    ).biflatMap(
      _ => {
        token = None
        EitherT(
          nestAuthorisationQueries.clearRefreshToken
            .map { _ =>
              Left(
                UpstreamErrorResponse(
                  "Nest authorisation required",
                  401,
                  401
                )
              )
            }
        )
      },
      accessToken => {
        token = Some(
          NestToken(
            accessToken = accessToken.access_token,
            expiresAt = System.currentTimeMillis() + accessToken.expires_in * 1000
          )
        )
        EitherT.rightT(accessToken.access_token)
      }
    )

  }
}
