package services

import java.time.{ Clock, Instant, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.global

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.stubbing.OngoingStubbing
import org.mockito.ArgumentMatchers.{ any, anyDouble, eq as eqTo }
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ HeatingOverride, ThermostatState }
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import models.nest.{ HvacStatus, NestDevice, NestTraits, ThermostatMode }
import queries.{ HeatingOverrideQueries, ScheduleQueries, ThermostatStateQueries }
import testUtils.BaseSpec

class HeatingControlServiceSpec extends BaseSpec with MockitoSugar {

  given HeaderCarrier    = HeaderCarrier()
  given ExecutionContext = global

  private val activeTemperature  = 22.0
  private val standbyTemperature = 16.0
  private val scheduleTarget     = 20.0

  private class Fixture(heatingEnabled: Boolean = true, trvEnabled: Boolean = true, sensorEnabled: Boolean = true) {
    val appConfig: AppConfig                      = mock[AppConfig]
    val nestConnector: NestConnector              = mock[NestConnector]
    val devicesService: DevicesService            = mock[DevicesService]
    val scheduleQueries: ScheduleQueries          = mock[ScheduleQueries]
    val overrideQueries: HeatingOverrideQueries   = mock[HeatingOverrideQueries]
    val thermostatQueries: ThermostatStateQueries = mock[ThermostatStateQueries]

    val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    when(appConfig.heatingControlIsEnabled).thenReturn(heatingEnabled)
    when(appConfig.trvControlIsEnabled).thenReturn(trvEnabled)
    when(appConfig.sensorControlIsEnabled).thenReturn(sensorEnabled)
    when(appConfig.heatingDemandActiveTemperatureCelsius).thenReturn(activeTemperature)
    when(appConfig.heatingDemandStandbyTemperatureCelsius).thenReturn(standbyTemperature)
    when(appConfig.heatingDemandMinimumOn).thenReturn(5.minutes)
    when(appConfig.heatingDemandMinimumOff).thenReturn(5.minutes)

    when(nestConnector.setTemperature(anyDouble())(using any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](()))

    when(overrideQueries.getOverride).thenReturn(Future.successful(None))

    // By default the thermostat has never been switched, so no minimum time applies
    when(thermostatQueries.get(any[String]())).thenReturn(Future.successful(None))
    when(thermostatQueries.set(any[String](), any[ThermostatState](), any[Instant]())).thenReturn(Future.unit)

    when(devicesService.getTrvs).thenReturn(Future.successful(Seq.empty[Trv]))
    when(devicesService.getTemperatureSensors(using any())).thenReturn(Future.successful(Seq.empty[TemperatureSensor]))

    private val nestService =
      new NestService(appConfig, nestConnector, overrideQueries, thermostatQueries, clock)(using global)

    val sut =
      new HeatingControlService(
        appConfig,
        nestService,
        devicesService
      )(using global)

    def givenNestDevice(
        name: String = "Name",
        deviceType: String = "deviceType",
        traits: NestTraits
    ): OngoingStubbing[EitherT[Future, UpstreamErrorResponse, NestDevice]] =
      when(nestConnector.getDevice()(using any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](NestDevice(name, deviceType, traits)))

    def givenOverride(mode: HeatingOverride): OngoingStubbing[Future[Option[HeatingOverride]]] =
      when(overrideQueries.getOverride).thenReturn(Future.successful(Some(mode)))

    def givenTrvs(trvs: Trv*): OngoingStubbing[Future[Seq[Trv]]] =
      when(devicesService.getTrvs).thenReturn(Future.successful(trvs))

    def givenSensors(sensors: TemperatureSensor*): OngoingStubbing[Future[Seq[TemperatureSensor]]] =
      when(devicesService.getTemperatureSensors).thenReturn(Future.successful(sensors))

    def givenTargetTemperature(temperature: Double): OngoingStubbing[Future[Double]] =
      when(devicesService.targetTemperature(any[String](), any[Int]())(using any()))
        .thenReturn(Future.successful(temperature))

    def verifyHeatingTurnedOn(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(activeTemperature))(using any())

    def verifyHeatingTurnedOff(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(standbyTemperature))(using any())

    def verifyThermostatNotChanged(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector, never()).setTemperature(anyDouble())(using any())
  }

  "HeatingControlService.onTrvUpdate" should {

    "do nothing when heating control is disabled" in new Fixture(heatingEnabled = false) {
      sut.onTrvUpdate(fakeTrv(name = "trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
    }

    "do nothing when TRV control is disabled" in new Fixture(trvEnabled = false) {
      sut.onTrvUpdate(fakeTrv(name = "trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
    }

    "turn the heating on when a TRV reports HEAT and the heating is off" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv(name = "trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "not change the thermostat when a TRV reports HEAT and the heating is already on" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating on when the thermostat is not in HEAT mode" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "turn the heating off when a TRV reports IDLE and no other device wants heat" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle), fakeTrv("trv2", runningState = RunningState.Idle))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0))
      givenTargetTemperature(16.0)

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyHeatingTurnedOff()
    }

    "not turn the heating off when another TRV is still requesting heat" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle), fakeTrv("trv2", runningState = RunningState.Heat))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating off when a sensor is below its target" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))
      givenSensors(fakeTemperatureSensor("sensor1", 18.0))
      givenTargetTemperature(21.0)

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not change the thermostat when the heating is already off" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating off when the thermostat is not in HEAT mode" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.HEATING))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "complete successfully when Nest returns an error" in new Fixture {
      when(nestConnector.getDevice()(using any()))
        .thenReturn(EitherT.leftT[Future, NestDevice](UpstreamErrorResponse("boom", 500)))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "complete successfully when an unexpected exception is thrown" in new Fixture {
      when(nestConnector.getDevice()(using any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, NestDevice](Future.failed(new RuntimeException("boom"))))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }
  }

  "HeatingControlService.onTemperatureSensorUpdate" should {

    "do nothing when heating control is disabled" in new Fixture(heatingEnabled = false) {
      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 15.0)).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
      verifyNoInteractions(scheduleQueries)
    }

    "do nothing when sensor control is disabled" in new Fixture(sensorEnabled = false) {
      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 15.0)).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
      verifyNoInteractions(scheduleQueries)
    }

    "turn the heating on when the sensor is below the scheduled target" in new Fixture {
      givenTargetTemperature(21.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "not turn the heating on when it is already on" in new Fixture {
      givenTargetTemperature(21.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "turn the heating off when the sensor is above target and no other device wants heat" in new Fixture {
      givenTargetTemperature(16.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0), fakeTemperatureSensor("sensor2", 21.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyHeatingTurnedOff()
    }

    "not turn the heating off when another sensor is below its target" in new Fixture {
      givenTargetTemperature(16.0)
      givenTargetTemperature(21.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0), fakeTemperatureSensor("sensor2", 18.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating off when a TRV is requesting heat" in new Fixture {
      givenTargetTemperature(16.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Heat))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "Turn the heating off when the sensor is exactly at its target" in new Fixture {
      givenTargetTemperature(scheduleTarget)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", scheduleTarget))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", scheduleTarget)).futureValue mustBe (())

      verifyHeatingTurnedOff()
    }

  }

  "HeatingControlService with a manual override" should {

    "turn the heating on for an idle TRV update when the override is heat" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "turn the heating on for a sensor above its target when the override is heat" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenTargetTemperature(16.0)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 25.0)).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "turn the heating off for a heat TRV update when the override is idle" in new Fixture {
      givenOverride(HeatingOverride.Idle)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyHeatingTurnedOff()
      verifyNoInteractions(devicesService)
    }

    "not turn the heating on when the override is heat but the thermostat is not in HEAT mode" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not read the override when heating control is disabled" in new Fixture(heatingEnabled = false) {
      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Heat)).futureValue mustBe (())

      verifyNoInteractions(overrideQueries)
      verifyNoInteractions(nestConnector)
    }

    "applyOverride heat turns the heating on immediately" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.applyOverride(HeatingOverride.Heat).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "applyOverride idle turns the heating off even when a TRV is requesting heat" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Heat))

      sut.applyOverride(HeatingOverride.Idle).futureValue mustBe (())

      verifyHeatingTurnedOff()
    }

    "applyOverride does nothing when heating control is disabled" in new Fixture(heatingEnabled = false) {
      sut.applyOverride(HeatingOverride.Heat).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
    }

    "applyAutomatic turns the heating on when a TRV is requesting heat" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Heat))

      sut.applyAutomatic().futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "applyAutomatic turns the heating off when no device is requesting heat" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.applyAutomatic().futureValue mustBe (())

      verifyHeatingTurnedOff()
    }
  }

}
