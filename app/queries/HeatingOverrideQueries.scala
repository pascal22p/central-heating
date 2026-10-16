package queries

import javax.inject.{Inject, Singleton}
import play.api.db.Database

import scala.concurrent.{ExecutionContext, Future}
import anorm.*
import models.{DatabaseExecutionContext, HeatingOverride, HeatingOverrideState}

@Singleton
class HeatingOverrideQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
)(implicit ec: ExecutionContext) {

  /** The table holds at most one row (id = 1). No row, or an expired row, means there is no override. */
  def getOverrideState: Future[Option[HeatingOverrideState]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT `mode`, `expires_at`
          |FROM heating_override
          |WHERE id = 1 AND (`expires_at` IS NULL OR `expires_at` > UTC_TIMESTAMP)""".stripMargin
      )
        .as(HeatingOverride.rowParser.singleOpt)
        .flatten
    }
  }(using databaseExecutionContext)

  def getOverride: Future[Option[HeatingOverride]] = getOverrideState.map(_.map(_.mode))

  /** `durationMinutes` empty means the override lasts until it is cleared. */
  def setOverride(mode: HeatingOverride, durationMinutes: Option[Int] = None): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO heating_override (id, `mode`, `expires_at`)
          |VALUES (1, {mode}, UTC_TIMESTAMP() + INTERVAL {minutes} MINUTE)
          |ON DUPLICATE KEY UPDATE
          |    `mode` = VALUES(`mode`),
          |    `expires_at` = VALUES(`expires_at`)""".stripMargin
      )
        .on("mode" -> mode.dbValue, "minutes" -> durationMinutes)
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def clearOverride(): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL("DELETE FROM heating_override WHERE id = 1").execute()
    }
    ()
  }(using databaseExecutionContext)

}
