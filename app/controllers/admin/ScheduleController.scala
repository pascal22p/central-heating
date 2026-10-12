package controllers.admin

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.libs.json.*
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

import actions.AuthJourney
import models.ScheduleDay
import queries.ScheduleQueries
import views.html.ScheduleFormView

@Singleton
final class ScheduleController @Inject() (
    cc: ControllerComponents,
    scheduleQueries: ScheduleQueries,
    authJourney: AuthJourney,
    scheduleFormView: ScheduleFormView
)(using ec: ExecutionContext)
    extends AbstractController(cc)
    with I18nSupport {

  private val defaultJson =
    """[
      |  {
      |    "start": "06:00",
      |    "end": "22:00",
      |    "temperature": 20.5
      |  }
      |]
      |""".stripMargin

  def edit(topic: String): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    scheduleQueries.getSchedule(topic).map { schedule =>
      val json =
        if (schedule.periods.isEmpty) defaultJson
        else Json.prettyPrint(Json.toJson(schedule.periods))

      Ok(scheduleFormView(topic, json))
    }
  }

  def save(topic: String): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    val data = request.body.asFormUrlEncoded
      .flatMap(_.get("data"))
      .getOrElse(Seq.empty)
      .headOption
      .getOrElse("")

    Try(Json.parse(data)) match {
      case Success(json) =>
        json.validate[Seq[models.SchedulePeriod]] match {
          case JsSuccess(periods, _) =>
            val schedule = ScheduleDay(periods)

            scheduleQueries.saveSchedule(topic, schedule).map { _ =>
              Redirect(controllers.admin.routes.ScheduleController.edit(topic))
            }

          case JsError(_) =>
            Future.successful(
              BadRequest(
                scheduleFormView(
                  topic,
                  data,
                  Some("Enter valid period JSON.")
                )
              )
            )
        }

      case Failure(error) =>
        Future.successful(
          BadRequest(
            scheduleFormView(
              topic,
              data,
              Some(s"Enter valid period JSON. $error")
            )
          )
        )
    }
  }
}
