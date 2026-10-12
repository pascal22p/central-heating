package models

import java.time.LocalTime

final case class ScheduleDay(periods: Seq[SchedulePeriod])
final case class SchedulePeriod(start: LocalTime, end: LocalTime, temperature: Double)
