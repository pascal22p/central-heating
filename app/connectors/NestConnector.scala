package connectors

import javax.inject.*
import play.api.libs.json.*
import play.api.libs.ws.writeableOf_JsValue
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.{ HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse }
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*

import com.google.inject.name.Named
import com.google.inject.Inject

import cats.data.EitherT
import config.AppConfig
import models.nest.NestDevice
import models.LoggingWithRequest
import services.CacheService

trait NestConnector {
  def getDevice()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, NestDevice]

  def setTemperature(temperatureCelsius: Double)(
      implicit hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Unit]

  def setHeatingMode()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit]
}

class CachingNestConnector @Inject() (
    @Named("default") underlying: NestConnector,
    cacheService: CacheService
)(implicit ec: ExecutionContext)
    extends NestConnector {

  private val cacheKey: String = "NestThermostat"

  def getDevice()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, NestDevice] = {
    cacheService.cache(cacheKey) {
      underlying.getDevice()
    }(using NestDevice.cacheFormat)
  }

  def setHeatingMode()(implicit hc: HeaderCarrier): EitherT[scala.concurrent.Future, UpstreamErrorResponse, Unit] = {
    for {
      _      <- EitherT.liftF(cacheService.deleteFromCache(cacheKey))
      result <- underlying.setHeatingMode()
    } yield result
  }

  def setTemperature(
      temperatureCelsius: Double
  )(implicit hc: HeaderCarrier): EitherT[scala.concurrent.Future, UpstreamErrorResponse, Unit] = {
    for {
      _      <- EitherT.liftF(cacheService.deleteFromCache(cacheKey))
      result <- underlying.setTemperature(temperatureCelsius)
    } yield result
  }
}

@Singleton
class DefaultNestConnector @Inject() (
    appConfig: AppConfig,
    http: HttpClientV2,
    nestConnectorOAuth: NestConnectorOAuth
)(implicit ec: ExecutionContext)
    extends NestConnector
    with LoggingWithRequest {

  def getDevice()(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, NestDevice] = {
    nestConnectorOAuth.getValidToken().flatMap { accessToken =>
      val path = s"/v1/enterprises/${appConfig.nestProjectId}/devices/${appConfig.nestDeviceId}"
      val url  = s"${appConfig.nestApiHost}$path"

      EitherT(
        http
          .get(url"$url")
          .setHeader(
            "Authorization" -> s"Bearer $accessToken"
          )
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      )
        .map { response =>
          response.json.as[NestDevice]
        }
    }
  }

  def setTemperature(
      temperatureCelsius: Double
  )(implicit hc: HeaderCarrier): EitherT[Future, UpstreamErrorResponse, Unit] = {

    nestConnectorOAuth.getValidToken().flatMap { accessToken =>
      val path =
        s"/v1/enterprises/${appConfig.nestProjectId}/devices/${appConfig.nestDeviceId}:executeCommand"

      val url =
        s"${appConfig.nestApiHost}$path"

      val body = Json.obj(
        "command" -> "sdm.devices.commands.ThermostatTemperatureSetpoint.SetHeat",
        "params"  -> Json.obj(
          "heatCelsius" -> temperatureCelsius
        )
      )

      EitherT(
        http
          .post(url"$url")
          .setHeader(
            "Authorization" -> s"Bearer $accessToken"
          )
          .withBody(body)
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      ).map { _ =>
        ()
      }
    }
  }

  def setHeatingMode()(
      implicit hc: HeaderCarrier
  ): EitherT[Future, UpstreamErrorResponse, Unit] = {

    nestConnectorOAuth.getValidToken().flatMap { accessToken =>
      val path =
        s"/v1/enterprises/${appConfig.nestProjectId}/devices/${appConfig.nestDeviceId}:executeCommand"

      val url =
        s"${appConfig.nestApiHost}$path"

      val body = Json.obj(
        "command" -> "sdm.devices.commands.ThermostatMode.SetMode",
        "params"  -> Json.obj(
          "mode" -> "HEAT"
        )
      )

      EitherT(
        http
          .post(url"$url")
          .setHeader(
            "Authorization" -> s"Bearer $accessToken"
          )
          .withBody(body)
          .execute[Either[UpstreamErrorResponse, HttpResponse]]
      ).map { _ =>
        ()
      }
    }
  }

}
