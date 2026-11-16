package models

import play.api.libs.json.{ Json, OFormat }

final case class HeatingSchedule(
    description: String,
    weekdays: Seq[HeatingScheduleTransition],
    weekend: Seq[HeatingScheduleTransition]
) derives CanEqual {
  def weekdaysString: String = weekdays
    .map { transition =>
      s"${transition.start.toString}/${"%.2f".format(transition.temperature)}"
    }
    .mkString(" ")

  def weekendString: String = weekend
    .map { transition =>
      s"${transition.start.toString}/${"%.2f".format(transition.temperature)}"
    }
    .mkString(" ")
}

object HeatingSchedule {
  implicit val formats: OFormat[HeatingSchedule] = Json.format[HeatingSchedule]
}
