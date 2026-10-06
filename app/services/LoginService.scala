package services

import javax.inject.{ Inject, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

import com.password4j.Password

import cats.data.OptionT
import models.UserData
import queries.SessionSqlQueries

@Singleton
class LoginService @Inject() (mariadbQueries: SessionSqlQueries)(
    implicit ec: ExecutionContext
) {
  def getUserData(username: String, password: String): OptionT[Future, UserData] = {
    mariadbQueries.getUserData(username).transform {
      case Some(result) =>
        val verified =
          Password.check(password, result.hashedPassword).withBcrypt()
        if (verified) Some(result) else None
      case _ => None
    }
  }
}
