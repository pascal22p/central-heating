package models

import play.api.libs.json.{ Format, Json }

final case class DeviceHeatingSchedule(id: Int, heatingSchedule: HeatingSchedule)

object DeviceHeatingSchedule {
  implicit val format: Format[DeviceHeatingSchedule] = Json.format[DeviceHeatingSchedule]
}
