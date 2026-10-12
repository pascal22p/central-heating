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
    (JsPath \ "temperature")
      .read[Double]
      .and((JsPath \ "humidity").read[Double])
      .and((JsPath \ "battery").read[Double])((temperature, humidity, battery) =>
        TemperatureSensor("", temperature, humidity, battery)
      )

}
