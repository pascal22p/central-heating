package models

import play.api.libs.json.Reads

final case class NestDevice(
    name: String,
    deviceType: String,
    traits: NestTraits
)

final case class NestTraits(
    connectivity: String,
    temperatureCelsius: Double,
    humidityPercent: Double,
    thermostatMode: String,
    heatSetpointCelsius: Option[Double],
    coolSetpointCelsius: Option[Double],
    hvacStatus: String,
    ecoMode: String,
    ecoHeatCelsius: Double,
    ecoCoolCelsius: Double
)

object NestDevice {

  implicit val nestDeviceReads: Reads[NestDevice] = Reads { json =>
    val traits = json \ "traits"

    for {
      name       <- (json \ "name").validate[String]
      deviceType <- (json \ "type").validate[String]
    } yield NestDevice(
      name = name,
      deviceType = deviceType,
      traits = NestTraits(
        connectivity = (traits \ "sdm.devices.traits.Connectivity" \ "status").as[String],

        temperatureCelsius = (traits \ "sdm.devices.traits.Temperature" \ "ambientTemperatureCelsius")
          .as[Double],

        humidityPercent = (traits \ "sdm.devices.traits.Humidity" \ "ambientHumidityPercent")
          .as[Double],

        thermostatMode = (traits \ "sdm.devices.traits.ThermostatMode" \ "mode")
          .as[String],

        heatSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "heatCelsius")
          .asOpt[Double],

        coolSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "coolCelsius")
          .asOpt[Double],

        hvacStatus = (traits \ "sdm.devices.traits.ThermostatHvac" \ "status")
          .as[String],

        ecoMode = (traits \ "sdm.devices.traits.ThermostatEco" \ "mode")
          .as[String],

        ecoHeatCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "heatCelsius")
          .as[Double],

        ecoCoolCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "coolCelsius")
          .as[Double],
      )
    )
  }
}
