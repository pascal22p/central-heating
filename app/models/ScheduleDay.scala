package models

import java.time.LocalTime
import play.api.libs.json.{ Format, Json }

final case class ScheduleDay(periods: Seq[SchedulePeriod])

final case class SchedulePeriod(start: LocalTime, end: LocalTime, temperature: Double)

object SchedulePeriod {
  given Format[SchedulePeriod] = Json.format[SchedulePeriod]
}
