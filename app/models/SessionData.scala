package models

import play.api.libs.json.{ Json, OFormat }

final case class SessionData(userData: Option[UserData] = None)

object SessionData {
  implicit val format: OFormat[SessionData] = Json.format[SessionData]
}
