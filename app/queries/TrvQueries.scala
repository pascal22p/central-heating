package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.Json
import scala.concurrent.Future

import anorm.*
import models.{ DatabaseExecutionContext, Trv }

@Singleton
final class TrvQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  def saveTrv(trv: Trv): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO trvs (
          |    name,
          |    data,
          |    expires_at
          |)
          |VALUES (
          |    {name},
          |    {data},
          |    CURRENT_TIMESTAMP + INTERVAL 1 HOUR
          |)
          |ON DUPLICATE KEY UPDATE
          |    data = VALUES(data),
          |    expires_at = CURRENT_TIMESTAMP + INTERVAL 1 HOUR""".stripMargin
      )
        .on(
          "name" -> trv.name,
          "data" -> Json.stringify(Json.toJson(trv)(using Trv.sqlFormat))
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def getTrvs: Future[Seq[Trv]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM trvs
          |WHERE expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .as(
          SqlParser
            .str("data")
            .map {
              case data =>
                Json
                  .parse(data)
                  .as[Trv](using Trv.sqlFormat)
            }
            .*
        )
    }
  }(using databaseExecutionContext)

  def getTrv(deviceName: String): Future[Option[Trv]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM trvs
          |WHERE name = {name} AND expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("name" -> deviceName)
        .as(
          SqlParser
            .str("data")
            .map {
              case data =>
                Json
                  .parse(data)
                  .as[Trv](using Trv.sqlFormat)
            }
            .singleOpt
        )
    }
  }(using databaseExecutionContext)

}
