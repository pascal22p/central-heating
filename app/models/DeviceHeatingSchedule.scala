package models

import play.api.libs.json.{ Format, Json }

import anorm.*

final case class DeviceHeatingSchedule(id: Int, heatingSchedule: HeatingSchedule)

object DeviceHeatingSchedule {
  implicit val format: Format[DeviceHeatingSchedule] = Json.format[DeviceHeatingSchedule]

  val parser: RowParser[DeviceHeatingSchedule] =
    (SqlParser.int("id") ~ HeatingSchedule.sqlParser).map {
      case id ~ schedule =>
        DeviceHeatingSchedule(id, schedule)
    }
}
