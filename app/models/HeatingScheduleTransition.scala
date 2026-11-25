package models

import java.time.LocalTime
import play.api.libs.json.{ Json, OFormat }

final case class HeatingScheduleTransition(start: LocalTime, temperature: Double) derives CanEqual

object HeatingScheduleTransition {
  implicit val formats: OFormat[HeatingScheduleTransition] = Json.format[HeatingScheduleTransition]
}
