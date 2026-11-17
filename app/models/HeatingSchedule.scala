package models

import java.text.DecimalFormat
import play.api.libs.json.{ Json, OFormat }

import anorm.*
import anorm.SqlParser.*
import models.mqtt.TrvScheduleList

final case class HeatingSchedule(
    description: String,
    weekdays: Seq[HeatingScheduleTransition],
    weekend: Seq[HeatingScheduleTransition]
) derives CanEqual {
  require(
    weekdays.sizeIs < 13,
    "Weekdays schedule must contain at most 12 transitions"
  )

  require(
    weekend.sizeIs < 13,
    "Weekend schedule must contain at most 12 transitions"
  )

  def toTrvSchedule: TrvScheduleList = {
    val weekdaysString = weekdays
      .map { transition =>
        s"${transition.start.toString}/${new DecimalFormat("0.##").format(transition.temperature)}"
      }
      .mkString(" ")

    val weekendString = weekend
      .map { transition =>
        s"${transition.start.toString}/${new DecimalFormat("0.##").format(transition.temperature)}"
      }
      .mkString(" ")

    TrvScheduleList(
      weekdaysString,
      weekdaysString,
      weekdaysString,
      weekdaysString,
      weekdaysString,
      weekendString,
      weekendString
    )
  }
}

object HeatingSchedule {
  implicit val formats: OFormat[HeatingSchedule] = Json.format[HeatingSchedule]

  val sqlParser: RowParser[HeatingSchedule] =
    (str("description") ~ str("schedule")).map {
      case description ~ jsonString =>
        val parsed = Json.parse(jsonString)
        HeatingSchedule(
          description = description,
          weekdays = (parsed \ "weekdays").as[Seq[HeatingScheduleTransition]].sortBy(_.start),
          weekend = (parsed \ "weekend").as[Seq[HeatingScheduleTransition]].sortBy(_.start)
        )
    }
}
