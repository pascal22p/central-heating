package models

import anorm.*

import java.time.{Instant, LocalDateTime, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A manual override of the automatic heating control. No stored override means the heating is automatic. */
enum HeatingOverride derives CanEqual {
  case Heat, Idle

  def dbValue: String = this match {
    case Heat => "heat"
    case Idle => "idle"
  }
}

object HeatingOverride {
  val rowParser: RowParser[Option[HeatingOverrideState]] =
    (SqlParser.str("mode") ~ SqlParser.get[Option[LocalDateTime]]("expires_at")).map { case mode ~ expiresAt =>
      HeatingOverride.fromDbValue(mode).map(HeatingOverrideState(_, expiresAt.map(_.toInstant(ZoneOffset.UTC))))
    }

  def fromDbValue(value: String): Option[HeatingOverride] =
    value.toLowerCase(Locale.ENGLISH) match {
      case "heat" => Some(HeatingOverride.Heat)
      case "idle" => Some(HeatingOverride.Idle)
      case _      => None
    }

  /** Durations (in minutes) offered to the user; the empty option means "until switched back to automatic". */
  val durationOptions: Seq[(Int, String)] = Seq(
    30   -> "30 minutes",
    60   -> "1 hour",
    120  -> "2 hours"
  )

  val defaultDurationMinutes: Int = 30
}

/** An active override; `expiresAt` is empty when it lasts until it is cleared. */
final case class HeatingOverrideState(mode: HeatingOverride, expiresAt: Option[Instant]) derives CanEqual

object HeatingOverrideState {
  private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.UK)

  def formatExpiry(instant: Instant): String = formatter.format(instant.atZone(ZoneId.of("Europe/London")))
}
