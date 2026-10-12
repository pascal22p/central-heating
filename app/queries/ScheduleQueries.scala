package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.Json
import scala.concurrent.Future

import anorm.*
import models.{ DatabaseExecutionContext, ScheduleDay, SchedulePeriod }

@Singleton
final class ScheduleQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  def saveSchedule(topic: String, schedule: ScheduleDay): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO sensor_schedules (
          |    topic,
          |    periods
          |)
          |VALUES (
          |    {topic},
          |    {periods}
          |)
          |ON DUPLICATE KEY UPDATE
          |    periods = VALUES(periods)""".stripMargin
      )
        .on(
          "topic"   -> topic,
          "periods" -> s"${Json.toJson(schedule.periods)}"
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def getSchedule(topic: String): Future[ScheduleDay] = Future {
    db.withConnection { implicit conn =>
      val periods = SQL(
        """SELECT periods
          |FROM sensor_schedules
          |WHERE topic = {topic}""".stripMargin
      )
        .on("topic" -> topic)
        .as(
          SqlParser
            .str("periods")
            .map {
              case data =>
                Json
                  .parse(data)
                  .as[Seq[SchedulePeriod]]
            }
            .singleOpt
        )
        .getOrElse(Seq.empty)
      ScheduleDay(periods)
    }
  }(using databaseExecutionContext)

}
