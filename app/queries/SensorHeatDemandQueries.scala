package queries

import java.time.{ Instant, LocalDateTime, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import scala.concurrent.Future

import anorm.*
import models.{ DatabaseExecutionContext, HeatDemand, SensorHeatDemand }

@Singleton
class SensorHeatDemandQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  /** The last stored demand of a sensor, or none when the sensor has never been seen. */
  def get(sensorName: String): Future[Option[SensorHeatDemand]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT `demand`, `changed_at`
          |FROM sensor_heat_demand
          |WHERE `topic` = {topic}""".stripMargin
      )
        .on("topic" -> sensorName)
        .as(SensorHeatDemand.rowParser.singleOpt)
        .flatten
    }
  }(using databaseExecutionContext)

  /** Stores the demand of a sensor; `changedAt` is when the demand changed (UTC). */
  def set(sensorName: String, demand: HeatDemand, changedAt: Instant): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO sensor_heat_demand (`topic`, `demand`, `changed_at`)
          |VALUES ({topic}, {demand}, {changedAt})
          |ON DUPLICATE KEY UPDATE
          |    `demand` = VALUES(`demand`),
          |    `changed_at` = VALUES(`changed_at`)""".stripMargin
      )
        .on(
          "topic"     -> sensorName,
          "demand"    -> demand.dbValue,
          "changedAt" -> LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC)
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

}
