package queries

import java.time.{ Instant, LocalDateTime, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import scala.concurrent.Future

import anorm.*
import models.{ DatabaseExecutionContext, ThermostatChange, ThermostatState }

@Singleton
class ThermostatStateQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  /** The last state the thermostat was put in, or none when it has never been switched. */
  def get(device: String): Future[Option[ThermostatChange]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT `state`, `changed_at`
          |FROM thermostat_state
          |WHERE `device` = {device}""".stripMargin
      )
        .on("device" -> device)
        .as(ThermostatChange.rowParser.singleOpt)
        .flatten
    }
  }(using databaseExecutionContext)

  /**
   * Stores the state the thermostat was put in. `changedAt` (UTC) is only kept when the state differs from the stored
   * one, so the stored time is always the time of the last real change.
   */
  def set(device: String, state: ThermostatState, changedAt: Instant): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO thermostat_state (`device`, `state`, `changed_at`)
          |VALUES ({device}, {state}, {changedAt})
          |ON DUPLICATE KEY UPDATE
          |    `changed_at` = IF(`state` = VALUES(`state`), `changed_at`, VALUES(`changed_at`)),
          |    `state` = VALUES(`state`)""".stripMargin
      )
        .on(
          "device"    -> device,
          "state"     -> state.dbValue,
          "changedAt" -> LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC)
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

}
