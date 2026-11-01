package models.mqtt

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{ Format, JsPath, Json, Reads }

final case class TemperatureSensor(
    name: String,
    temperature: Double,
    humidity: Double,
    battery: Double
)

object TemperatureSensor {
  val sqlFormat: Format[TemperatureSensor] =
    Json.format[TemperatureSensor]

  given Reads[TemperatureSensor] =
    (JsPath \ "name")
      .read[String]
      .and((JsPath \ "temperature").read[Double])
      .and((JsPath \ "humidity").read[Double])
      .and((JsPath \ "battery").read[Double])((name, temperature, humidity, battery) =>
        TemperatureSensor(name, temperature, humidity, battery)
      )
}
