package controllers.admin

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import config.AppConfig
import queries.{ DevicesQueries, ScheduleQueries }
import services.MqttPublisherService
import views.html.admin.{ TrvScheduleUpdateSentView, TrvSchedulesView }

@Singleton
final class TrvAdminController @Inject() (
    cc: ControllerComponents,
    authJourney: AuthJourney,
    appConfig: AppConfig,
    devicesQueries: DevicesQueries,
    scheduleQueries: ScheduleQueries,
    mqttPublisherService: MqttPublisherService,
    trvSchedulesView: TrvSchedulesView,
    trvScheduleUpdateSentView: TrvScheduleUpdateSentView
)(using ec: ExecutionContext)
    extends AbstractController(cc)
    with I18nSupport {

  def showSchedules(device: Option[String]): Action[AnyContent] =
    authJourney.authWithAdminRight.async { implicit request =>
      given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      val devices = appConfig.trvDevices.toSeq.sorted

      device match {
        case None =>
          Future.successful(Ok(trvSchedulesView(devices, None, None, None)))

        case Some(name) if !appConfig.trvDevices.contains(name) =>
          Future.successful(NotFound("Unknown TRV"))

        case Some(name) =>
          println(name)
          devicesQueries.getTrv(name).flatMap {
            case None =>
              Future.successful(
                NotFound("Trv not found")
              )

            case Some(trv) =>
              scheduleQueries
                .getScheduleByTopic(name, trv.scheduleActiveGroup)
                .value
                .map { expected =>
                  Ok(
                    trvSchedulesView(
                      devices,
                      Some(name),
                      Some(trv.toHeatingSchedule),
                      expected
                    )
                  )
                }
          }
      }
    }

  def updateSchedule(device: String): Action[AnyContent] =
    authJourney.authWithAdminRight.async { implicit request =>
      given HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

      if (!appConfig.trvDevices.contains(device)) {
        Future.successful(NotFound("Unknown TRV"))
      } else {
        devicesQueries.getTrv(device).flatMap {
          case None =>
            Future.successful(NotFound(s"No current state available for $device"))

          case Some(trv) if trv.scheduleGroupToEdit == trv.scheduleActiveGroup =>
            scheduleQueries
              .getScheduleByTopic(device, trv.scheduleActiveGroup)
              .foldF(Future.successful(NotFound(s"No schedule configured for $device"))) { expected =>
                mqttPublisherService
                  .updateTrvSchedule(
                    device,
                    expected.toTrvSchedule
                  )
                  .map { _ =>
                    Redirect(
                      controllers.admin.routes.TrvAdminController.scheduleUpdateSent(device)
                    )
                  }
              }

          case Some(trv) =>
            Future.successful(
              BadRequest(s"The schedule group to edit is not the same as the active group schedule for trv ${trv.name}")
            )
        }
      }
    }

  def scheduleUpdateSent(device: String): Action[AnyContent] =
    authJourney.authWithAdminRight { implicit request =>
      Ok(trvScheduleUpdateSentView(device))
    }
}
