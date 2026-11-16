package models.forms

import java.time.LocalTime
import play.api.data.*
import play.api.data.format.Formats.*
import play.api.data.Forms.*

import models.{ HeatingSchedule, HeatingScheduleTransition }

final case class ScheduleRow(start: Option[LocalTime], temperature: Option[Double]) derives CanEqual

final case class ScheduleFormData(description: String, weekdays: Seq[ScheduleRow], weekend: Seq[ScheduleRow])
    derives CanEqual

object ScheduleForm {

  private val row: Mapping[ScheduleRow] =
    mapping(
      "start"       -> optional(localTime("HH:mm")),
      "temperature" -> optional(of[Double])
    )((start, temperature) => ScheduleRow(start, temperature))(r => Some((r.start, r.temperature)))

  val form: Form[ScheduleFormData] =
    Form(
      mapping(
        "description" -> text,
        "weekdays"    -> seq(row),
        "weekend"     -> seq(row)
      )((description, weekdays, weekend) => ScheduleFormData(description, weekdays, weekend))(d =>
        Some((d.description, d.weekdays, d.weekend))
      )
    )

  private def toRows(ts: Seq[HeatingScheduleTransition]): Seq[ScheduleRow] =
    ts.map(t => ScheduleRow(Some(t.start), Some(t.temperature)))

  /** The form pre-filled with a stored schedule. Unused rows stay empty. */
  def fromSchedule(schedule: HeatingSchedule): Form[ScheduleFormData] =
    form.fill(ScheduleFormData(schedule.description, toRows(schedule.weekdays), toRows(schedule.weekend)))

  /** Empty form with 00:00 pre-filled on the first row of each day type. */
  def initial: Form[ScheduleFormData] = {
    val firstRow = Seq(ScheduleRow(Some(LocalTime.MIDNIGHT), None))
    form.fill(ScheduleFormData("", firstRow, firstRow))
  }

  // Rows missing a start time or a temperature (including empty rows) are dropped
  private def toTransitions(rows: Seq[ScheduleRow]): Seq[HeatingScheduleTransition] =
    rows.collect {
      case ScheduleRow(Some(start), Some(temperature)) =>
        HeatingScheduleTransition(start, temperature)
    }

  def toSchedule(data: ScheduleFormData): HeatingSchedule =
    HeatingSchedule(
      description = data.description,
      weekdays = toTransitions(data.weekdays),
      weekend = toTransitions(data.weekend)
    )
}
