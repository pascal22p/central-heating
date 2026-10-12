package models.nest

final case class NestTraits(
    connectivity: Connectivity,
    temperatureCelsius: Double,
    humidityPercent: Double,
    thermostatMode: ThermostatMode,
    heatSetpointCelsius: Option[Double],
    coolSetpointCelsius: Option[Double],
    hvacStatus: HvacStatus,
    ecoMode: EcoMode,
    ecoHeatCelsius: Double,
    ecoCoolCelsius: Double
)
