package models

import java.time.{ Instant, LocalDateTime, ZoneOffset }
import java.util.Locale

import anorm.*

/** The state the thermostat was last put in: heating requested, or standby. */
enum ThermostatState derives CanEqual {
  case Heat, Idle

  def dbValue: String = this match {
    case Heat => "heat"
    case Idle => "idle"
  }
}

object ThermostatState {

  def fromDbValue(value: String): Option[ThermostatState] =
    value.toLowerCase(Locale.ENGLISH) match {
      case "heat" => Some(ThermostatState.Heat)
      case "idle" => Some(ThermostatState.Idle)
      case _      => None
    }
}

/** The last time the thermostat was switched, which allows a minimum on and off time to be enforced. */
final case class ThermostatChange(state: ThermostatState, changedAt: Instant) derives CanEqual

object ThermostatChange {
  val rowParser: RowParser[Option[ThermostatChange]] =
    (SqlParser.str("state") ~ SqlParser.get[LocalDateTime]("changed_at")).map {
      case state ~ changedAt =>
        ThermostatState.fromDbValue(state).map(ThermostatChange(_, changedAt.toInstant(ZoneOffset.UTC)))
    }
}
