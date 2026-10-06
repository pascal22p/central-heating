package models

import play.api.libs.json.{ Json, OFormat }

import anorm.*
import anorm.SqlParser.*

final case class UserData(id: Int, username: String, hashedPassword: String, isAdmin: Boolean)

object UserData {
  implicit val format: OFormat[UserData] = Json.format[UserData]

  val mysqlParser: RowParser[UserData] =
    (get[Int]("id") ~
      get[String]("email") ~
      get[String]("password") ~
      get[Boolean]("is_admin")).map {
      case id ~ username ~ hashedPassword ~ isAdmin =>
        UserData(id, username, hashedPassword, isAdmin)
    }
}
