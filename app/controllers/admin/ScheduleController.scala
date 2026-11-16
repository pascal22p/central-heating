package controllers.admin

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }

import actions.AuthJourney
import models.forms.ScheduleForm
import queries.ScheduleQueries
import views.html.admin.{ EditScheduleView, SchedulesView }

@Singleton
final class ScheduleController @Inject() (
    cc: ControllerComponents,
    scheduleQueries: ScheduleQueries,
    authJourney: AuthJourney,
    schedulesView: SchedulesView,
    editScheduleView: EditScheduleView
)(using ec: ExecutionContext)
    extends AbstractController(cc)
    with I18nSupport {

  def showSchedules: Action[AnyContent] = authJourney.guestAccess.async { implicit request =>
    scheduleQueries.getSchedules.map(schedules => Ok(schedulesView(schedules)))
  }

  def editHeatingSchedule(id: Int): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    scheduleQueries
      .getScheduleById(id)
      .map { schedule =>
        Ok(
          editScheduleView(
            ScheduleForm.fromSchedule(schedule),
            controllers.admin.routes.ScheduleController.saveHeatingSchedule(id)
          )
        )
      }
      .getOrElse(NotFound("Schedule not found"))
  }

  def saveHeatingSchedule(id: Int): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    ScheduleForm.form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Future.successful(
            BadRequest(
              editScheduleView(formWithErrors, controllers.admin.routes.ScheduleController.saveHeatingSchedule(id))
            )
          ),
        data =>
          scheduleQueries.upsertHeatingSchedule(Some(id), ScheduleForm.toSchedule(data)).map { _ =>
            Redirect(controllers.admin.routes.ScheduleController.showSchedules)
          }
      )
  }

  def newHeatingSchedule: Action[AnyContent] = authJourney.authWithAdminRight { implicit request =>
    Ok(
      editScheduleView(
        ScheduleForm.initial,
        controllers.admin.routes.ScheduleController.createHeatingSchedule,
        "Add schedule"
      )
    )
  }

  def createHeatingSchedule: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    ScheduleForm.form
      .bindFromRequest()
      .fold(
        formWithErrors =>
          Future.successful(
            BadRequest(
              editScheduleView(
                formWithErrors,
                controllers.admin.routes.ScheduleController.createHeatingSchedule,
                "Add schedule"
              )
            )
          ),
        data =>
          scheduleQueries.upsertHeatingSchedule(None, ScheduleForm.toSchedule(data)).map { id =>
            Redirect(controllers.admin.routes.ScheduleController.showSchedules)
              .flashing("success" -> s"Schedule $id added")
          }
      )
  }
}
