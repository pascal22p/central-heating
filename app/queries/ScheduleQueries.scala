package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.Json
import scala.concurrent.Future

import anorm.*
import cats.data.OptionT
import models.{ DatabaseExecutionContext, DeviceHeatingSchedule, HeatingSchedule }

@Singleton
final class ScheduleQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  def insertSchedule(schedule: HeatingSchedule): Future[Long] = Future {
    db.withConnection { implicit conn =>
      SQL("INSERT INTO schedules (schedule) VALUES ({schedule})")
        .on("schedule" -> Json.stringify(Json.toJson(schedule)))
        .executeInsert(SqlParser.scalar[Long].single)
    }
  }(using databaseExecutionContext)

  def getScheduleByTopic(topic: String, activeGroup: Int): OptionT[Future, HeatingSchedule] = OptionT(Future {
    db.withConnection { implicit conn =>
      SQL("""SELECT schedules.description, schedules.schedule FROM topics
            |LEFT JOIN topic_schedule ON topics.id = topic_schedule.topic
            |LEFT JOIN schedules ON topic_schedule.schedule = schedules.id
            |WHERE topics.name = {topic} AND topic_schedule.schedule_number = {activeGroup}""".stripMargin)
        .on("topic" -> topic, "activeGroup" -> activeGroup)
        .as(HeatingSchedule.sqlParser.singleOpt)
    }
  }(using databaseExecutionContext))

  def getScheduleById(id: Int): OptionT[Future, HeatingSchedule] = OptionT(Future {
    db.withConnection { implicit conn =>
      SQL("""SELECT description, schedule FROM schedules
            |WHERE id = {id}""".stripMargin)
        .on("id" -> id)
        .as(HeatingSchedule.sqlParser.singleOpt)
    }
  }(using databaseExecutionContext))

  def getAllSchedules: Future[Seq[DeviceHeatingSchedule]] = Future {
    db.withConnection { implicit conn =>
      SQL("SELECT id, description, schedule FROM schedules ORDER BY id")
        .as(DeviceHeatingSchedule.parser.*)
    }
  }(using databaseExecutionContext)

  def upsertHeatingSchedule(id: Option[Int], schedule: HeatingSchedule): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO schedules (id, description, schedule)
          |VALUES ({id}, {description}, {schedule})
          |ON DUPLICATE KEY UPDATE
          |    schedule = VALUES(schedule),
          |    description = VALUES(description)
          """.stripMargin
      )
        .on(
          "id"          -> id,
          "description" -> schedule.description,
          "schedule"    -> Json.stringify(Json.toJson(schedule))
        )
        .executeUpdate()
    }
    ()
  }(using databaseExecutionContext)

}
