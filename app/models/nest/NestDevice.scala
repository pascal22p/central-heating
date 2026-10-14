package models.nest

import play.api.libs.json.{ Format, Json, OFormat, Reads }

import models.*
import utils.EnumFormats

final case class NestDevice(
    name: String,
    deviceType: String,
    traits: NestTraits
)

object NestDevice {

  val cacheFormat: Format[NestDevice] = {
    given Format[Connectivity]   = EnumFormats.enumFormat(Connectivity.values)
    given Format[ThermostatMode] = EnumFormats.enumFormat(ThermostatMode.values)
    given Format[HvacStatus]     = EnumFormats.enumFormat(HvacStatus.values)
    given Format[EcoMode]        = EnumFormats.enumFormat(EcoMode.values)
    given Format[NestTraits]     = Json.format[NestTraits]
    Json.format[NestDevice]
  }

  implicit val nestDeviceReads: Reads[NestDevice] = Reads { json =>
    val traits = json \ "traits"

    for {
      name       <- (json \ "name").validate[String]
      deviceType <- (json \ "type").validate[String]
    } yield NestDevice(
      name = name,
      deviceType = deviceType,
      traits = NestTraits(
        connectivity = (traits \ "sdm.devices.traits.Connectivity" \ "status")
          .as[Connectivity](using EnumFormats.enumFormat(Connectivity.values)),

        temperatureCelsius = (traits \ "sdm.devices.traits.Temperature" \ "ambientTemperatureCelsius")
          .as[Double],

        humidityPercent = (traits \ "sdm.devices.traits.Humidity" \ "ambientHumidityPercent")
          .as[Double],

        thermostatMode = (traits \ "sdm.devices.traits.ThermostatMode" \ "mode")
          .as[ThermostatMode](using EnumFormats.enumFormat(ThermostatMode.values)),

        heatSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "heatCelsius")
          .asOpt[Double],

        coolSetpointCelsius = (traits \ "sdm.devices.traits.ThermostatTemperatureSetpoint" \ "coolCelsius")
          .asOpt[Double],

        hvacStatus = (traits \ "sdm.devices.traits.ThermostatHvac" \ "status")
          .as[HvacStatus](using EnumFormats.enumFormat(HvacStatus.values)),

        ecoMode = (traits \ "sdm.devices.traits.ThermostatEco" \ "mode")
          .as[EcoMode](using EnumFormats.enumFormat(EcoMode.values)),

        ecoHeatCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "heatCelsius")
          .as[Double],

        ecoCoolCelsius = (traits \ "sdm.devices.traits.ThermostatEco" \ "coolCelsius")
          .as[Double],
      )
    )
  }
}
