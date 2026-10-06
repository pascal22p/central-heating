package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.Json
import scala.concurrent.Future

import anorm.*
import anorm.SqlParser.*
import cats.data.OptionT
import models.*

@Singleton
final class SessionSqlQueries @Inject() (db: Database, databaseExecutionContext: DatabaseExecutionContext) {

  def getUserData(username: String): OptionT[Future, UserData] = OptionT(Future {
    db.withConnection { implicit conn =>
      SQL("""SELECT *
            |FROM admins
            |WHERE email = {email}""".stripMargin)
        .on("email" -> username)
        .as(UserData.mysqlParser.singleOpt)
    }
  }(using databaseExecutionContext))

  def getSessionData(sessionId: String): Future[Option[Session]] = Future {
    db.withConnection { implicit conn =>
      SQL("""SELECT *
            |FROM sessions
            |WHERE sessionId = {id}
            |AND timeStamp > CURRENT_TIMESTAMP - INTERVAL '15' MINUTE""".stripMargin)
        .on("id" -> sessionId)
        .as(Session.mysqlParser.singleOpt)
    }
  }(using databaseExecutionContext)

  def putSessionData(session: Session): Future[Option[String]] = Future {
    db.withConnection { implicit conn =>
      SQL("""INSERT INTO sessions (sessionId, sessionData)
            |VALUES ({id}, {data})
            |ON DUPLICATE KEY UPDATE
            |sessionData = VALUES(sessionData),
            |timeStamp = CURRENT_TIMESTAMP(6);
            |""".stripMargin)
        .on(
          "id"   -> session.sessionId,
          "data" -> Json.toJson(session.sessionData).toString
        )
        .executeInsert(str(1).singleOpt)
    }
  }(using databaseExecutionContext)

  def updateSessionData(session: Session): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""UPDATE sessions
            |SET sessionData = {data}
            |WHERE sessionId = {id}
            |""".stripMargin)
        .on("id" -> session.sessionId, "data" -> Json.toJson(session.sessionData).toString)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def removeSessionData(session: Session): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""DELETE FROM sessions
            |WHERE sessionId = {id}
            |""".stripMargin)
        .on("id" -> session.sessionId)
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def sessionKeepAlive(sessionId: String): Future[Int] = Future {
    db.withConnection { implicit conn =>
      SQL("""UPDATE sessions
            |SET timeStamp = CURRENT_TIMESTAMP
            |WHERE sessionId = {id}""".stripMargin)
        .on("id" -> sessionId)
        .executeUpdate()
    }
  }(using databaseExecutionContext)
}
