package models

import play.api.libs.json.{ Json, OFormat }

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
        s"${transition.start.toString}/${"%.2f".format(transition.temperature)}"
      }
      .mkString(" ")

    val weekendString = weekend
      .map { transition =>
        s"${transition.start.toString}/${"%.2f".format(transition.temperature)}"
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
}
