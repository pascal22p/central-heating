package controllers

import java.time.{ Clock, LocalTime }
import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthAction
import config.AppConfig
import connectors.NestConnector
import models.mqtt.TemperatureSensor
import queries.ScheduleQueries
import services.DevicesService
import views.html.DashboardView

@Singleton
class HomeController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthAction,
    appConfig: AppConfig,
    nestConnector: NestConnector,
    devicesService: DevicesService,
    scheduleQueries: ScheduleQueries,
    dashboardView: DashboardView,
    clock: Clock
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    given HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)

    for {
      nestResult     <- nestConnector.getDevice().value
      sensors        <- devicesService.getTemperatureSensors
      trvs           <- devicesService.getTrvs
      sensorStatuses <- Future.sequence(sensors.map(sensorStatus))
    } yield {
      nestResult match {
        case Right(device) =>
          Ok(dashboardView(device, sensorStatuses, trvs))

        case Left(error) =>
          InternalServerError(s"Unable to read Nest: ${error.message}")
      }
    }
  }

  private def sensorStatus(
      sensor: TemperatureSensor
  ): Future[(TemperatureSensor, Double)] =
    scheduleQueries.getSchedule(sensor.name).map { schedule =>
      val now    = LocalTime.now(clock)
      val target = schedule.periods
        .find(period => !now.isBefore(period.start) && !now.isAfter(period.end))
        .map(_.temperature)
        .getOrElse(appConfig.heatingDemandStandbyTemperatureCelsius)

      sensor -> target
    }
}
