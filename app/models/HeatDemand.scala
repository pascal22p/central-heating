package models

import java.time.{ Instant, LocalDateTime, ZoneOffset }
import java.util.Locale

import anorm.*

/** Whether a sensor currently asks for heat. */
enum HeatDemand derives CanEqual {
  case Heat, Idle

  def dbValue: String = this match {
    case Heat => "heat"
    case Idle => "idle"
  }
}

object HeatDemand {

  def fromDbValue(value: String): Option[HeatDemand] =
    value.toLowerCase(Locale.ENGLISH) match {
      case "heat" => Some(HeatDemand.Heat)
      case "idle" => Some(HeatDemand.Idle)
      case _      => None
    }

  /** Hysteresis (dead band) around the target temperature.
    *
    *   - Idle -> Heat when the temperature falls below `target - hysteresis`
    *   - Heat -> Idle when the temperature reaches `target + hysteresis`
    *   - In between, the previous demand is kept
    */
  def next(previous: HeatDemand, temperature: Double, target: Double, hysteresis: Double): HeatDemand =
    previous match {
      case Idle if temperature < target - hysteresis  => Heat
      case Heat if temperature >= target + hysteresis => Idle
      case unchanged                                  => unchanged
    }
}

/** The last demand of a sensor and when it last changed, which allows a minimum duration to be enforced. */
final case class SensorHeatDemand(demand: HeatDemand, changedAt: Instant) derives CanEqual

object SensorHeatDemand {
  val rowParser: RowParser[Option[SensorHeatDemand]] =
    (SqlParser.str("demand") ~ SqlParser.get[LocalDateTime]("changed_at")).map {
      case demand ~ changedAt =>
        HeatDemand.fromDbValue(demand).map(SensorHeatDemand(_, changedAt.toInstant(ZoneOffset.UTC)))
    }
}
