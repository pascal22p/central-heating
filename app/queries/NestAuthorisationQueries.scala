package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import scala.concurrent.Future

import anorm.*
import cats.data.OptionT
import models.DatabaseExecutionContext

@Singleton
final class NestAuthorisationQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  def getRefreshToken: OptionT[Future, String] = OptionT(Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT refresh_token
          |FROM nest_authorisation
          |WHERE id = 1""".stripMargin
      ).as(
        SqlParser.scalar[String].singleOpt
      )
    }
  }(using databaseExecutionContext))

  def saveRefreshToken(refreshToken: String): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO nest_authorisation (
          |    id,
          |    refresh_token
          |)
          |VALUES (
          |    1,
          |    {refreshToken}
          |)
          |ON DUPLICATE KEY UPDATE
          |    refresh_token = VALUES(refresh_token)""".stripMargin
      )
        .on(
          "refreshToken" -> refreshToken
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def clearRefreshToken: Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """DELETE FROM nest_authorisation
          |WHERE id = 1""".stripMargin
      ).execute()
    }
    ()
  }(using databaseExecutionContext)
}
