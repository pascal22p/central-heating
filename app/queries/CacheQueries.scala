package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.{ Json, Reads, Writes }
import scala.concurrent.Future

import anorm.{ SQL, SqlParser }
import models.DatabaseExecutionContext

@Singleton
class CacheQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  private val cacheTtl: Int = 10

  def getFromCache[A](key: String)(implicit reads: Reads[A]): Future[Option[A]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM cache
          |WHERE `key` = {key} AND `expires_at` > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("key" -> key)
        .as(
          SqlParser
            .str("data")
            .map(Json.parse(_).as[A])
            .singleOpt
        )
    }
  }(using databaseExecutionContext)

  def UpsertInCache[A](key: String, data: A)(implicit reads: Writes[A]): Future[Boolean] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO cache (
          |    `key`,
          |    `data`,
          |    `expires_at`
          |)
          |VALUES (
          |    {key},
          |    {data},
          |    CURRENT_TIMESTAMP + INTERVAL {ttl} MIN
          |)
          |ON DUPLICATE KEY UPDATE
          |    data = VALUES(data),
          |    expires_at = CURRENT_TIMESTAMP + INTERVAL {ttl} MIN""".stripMargin
      )
        .on(
          "key"  -> key,
          "data" -> Json.stringify(Json.toJson(data)),
          "ttl"  -> cacheTtl
        )
        .execute()
    }
  }(using databaseExecutionContext)

  def deleteFromCache(key: String) = Future {
    db.withConnection { implicit conn =>
      SQL(
        """DELETE FROM cache WHERE `key` = {key}""".stripMargin
      )
        .on("key" -> key)
        .execute()
    }
  }(using databaseExecutionContext)

}
