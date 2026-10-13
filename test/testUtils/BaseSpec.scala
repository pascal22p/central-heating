package testUtils

import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting
import play.api.Application

import org.scalatest.concurrent.{ IntegrationPatience, ScalaFutures }
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.play.PlaySpec

import models.mqtt.*
import models.nest.*

trait BaseSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with ScalaFutures
    with Injecting
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.mqtt.isEnabled"      -> false,
        "scheduler.nest.isEnabled"                  -> false,
        "scheduler.nest.startDelayInSeconds"        -> 2000,
        "scheduler.nest.schedulerIntervalInSeconds" -> 2000
      )

  implicit override lazy val app: Application = localGuiceApplicationBuilder().build()

  def fakeTrv(
      name: String = "Fake TRV",
      battery: Option[Int] = Some(100),
      externalTemperatureInput: Double = 0.0,
      heatPercentageHour: Double = 0.0,
      linkQuality: Int = 100,
      localTemperature: Double = 20.0,
      localTemperatureCalibration: Double = 0.0,
      occupiedHeatingSetpoint: Double = 20.0,
      runningState: RunningState = RunningState.Idle,
      systemMode: SystemMode = SystemMode.Heat,
      temperatureSensorSelection: TemperatureSensorSelection = TemperatureSensorSelection.Internal,
      openWindowDetected: Boolean = false,
      childLock: ChildLock = ChildLock.Unlocked,
      smartTemperatureControl: String = ""
  ): Trv =
    Trv(
      name,
      battery,
      externalTemperatureInput,
      heatPercentageHour,
      linkQuality,
      localTemperature,
      localTemperatureCalibration,
      occupiedHeatingSetpoint,
      runningState,
      systemMode,
      temperatureSensorSelection,
      openWindowDetected,
      childLock,
      smartTemperatureControl
    )

  def fakeNestTraits(
      connectivity: Connectivity = Connectivity.ONLINE,
      temperatureCelsius: Double = 20.0,
      humidityPercent: Double = 50.0,
      thermostatMode: ThermostatMode = ThermostatMode.HEAT,
      heatSetpointCelsius: Option[Double] = Some(20.0),
      coolSetpointCelsius: Option[Double] = None,
      hvacStatus: HvacStatus = HvacStatus.OFF,
      ecoMode: EcoMode = EcoMode.OFF,
      ecoHeatCelsius: Double = 16.0,
      ecoCoolCelsius: Double = 24.0
  ): NestTraits =
    NestTraits(
      connectivity = connectivity,
      temperatureCelsius = temperatureCelsius,
      humidityPercent = humidityPercent,
      thermostatMode = thermostatMode,
      heatSetpointCelsius = heatSetpointCelsius,
      coolSetpointCelsius = coolSetpointCelsius,
      hvacStatus = hvacStatus,
      ecoMode = ecoMode,
      ecoHeatCelsius = ecoHeatCelsius,
      ecoCoolCelsius = ecoCoolCelsius
    )

  def fakeTemperatureSensor(
      name: String = "Fake Temperature Sensor",
      temperature: Double = 20.0,
      humidity: Double = 50.0,
      battery: Double = 100.0
  ): TemperatureSensor =
    TemperatureSensor(
      name = name,
      temperature = temperature,
      humidity = humidity,
      battery = battery
    )
}
