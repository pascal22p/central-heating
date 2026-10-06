package models

import play.api.libs.json.{ Json, Reads }

final case class NestDevice(
    name: String,
    deviceType: String,
    traits: NestTraits
)

final case class NestTraits(
    connectivity: Option[String],
    temperatureCelsius: Option[Double],
    humidityPercent: Option[Double],
    thermostatMode: Option[String],
    heatSetpointCelsius: Option[Double],
    coolSetpointCelsius: Option[Double],
    hvacStatus: Option[String],
    ecoMode: Option[String],
    ecoHeatCelsius: Option[Double],
    ecoCoolCelsius: Option[Double],
    fanTimerMode: Option[String],
    fanTimerTimeout: Option[String]
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
        connectivity = (traits \ "sdm.devices.traits.Connectivity" \ "status").asOpt[String],

        temperatureCelsius = (traits \ "sdm.devices.traits.Temperature" \ "ambientTemperatureCelsius")
          .asOpt[Double],

        humidityPercent = (traits \ "sdm.devices.traits.Humidity" \ "ambientHumidityPercent")
          .asOpt[Double],

        thermostatMode = (traits \ "sdm.devices.traits.ThermostatMode" \ "mode")
          .asOpt[String],

        heatSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "heatCelsius")
          .asOpt[Double],

        coolSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "coolCelsius")
          .asOpt[Double],

        hvacStatus = (traits \ "sdm.devices.traits.ThermostatHvac" \ "status")
          .asOpt[String],

        ecoMode = (traits \ "sdm.devices.traits.ThermostatEco" \ "mode")
          .asOpt[String],

        ecoHeatCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "heatCelsius")
          .asOpt[Double],

        ecoCoolCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "coolCelsius")
          .asOpt[Double],

        fanTimerMode = (traits \ "sdm.devices.traits.Fan" \ "timerMode")
          .asOpt[String],

        fanTimerTimeout = (traits \ "sdm.devices.traits.Fan" \ "timerTimeout")
          .asOpt[String]
      )
    )
  }
}
