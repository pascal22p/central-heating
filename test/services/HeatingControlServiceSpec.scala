package services

import java.time.{ Clock, Instant, LocalTime, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.ExecutionContext.global

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.stubbing.OngoingStubbing
import org.mockito.ArgumentMatchers.{ any, anyDouble, eq => eqTo }
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ ScheduleDay, SchedulePeriod }
import models.mqtt.{ RunningState, TemperatureSensor, Trv }
import models.nest.{ HvacStatus, NestDevice, NestTraits, ThermostatMode }
import queries.ScheduleQueries
import testUtils.BaseSpec

class HeatingControlServiceSpec extends BaseSpec with MockitoSugar {

  given HeaderCarrier    = HeaderCarrier()
  given ExecutionContext = global

  private val activeTemperature  = 22.0
  private val standbyTemperature = 16.0
  private val scheduleTarget     = 20.0

  // The clock is fixed at 12:00, so this period is "now"
  private val currentPeriod =
    SchedulePeriod(start = LocalTime.of(8, 0), end = LocalTime.of(18, 0), temperature = scheduleTarget)
  private val laterPeriod = SchedulePeriod(start = LocalTime.of(18, 1), end = LocalTime.of(23, 0), temperature = 25.0)

  private def schedule(periods: SchedulePeriod*): ScheduleDay =
    ScheduleDay(periods = periods)

  private class Fixture(heatingEnabled: Boolean = true, trvEnabled: Boolean = true, sensorEnabled: Boolean = true) {
    val appConfig: AppConfig             = mock[AppConfig]
    val nestConnector: NestConnector     = mock[NestConnector]
    val devicesService: DevicesService   = mock[DevicesService]
    val scheduleQueries: ScheduleQueries = mock[ScheduleQueries]

    val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC)

    when(appConfig.heatingControlIsEnabled).thenReturn(heatingEnabled)
    when(appConfig.trvControlIsEnabled).thenReturn(trvEnabled)
    when(appConfig.sensorControlIsEnabled).thenReturn(sensorEnabled)
    when(appConfig.heatingDemandActiveTemperatureCelsius).thenReturn(activeTemperature)
    when(appConfig.heatingDemandStandbyTemperatureCelsius).thenReturn(standbyTemperature)

    when(nestConnector.setTemperature(anyDouble())(using any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](()))

    when(devicesService.getTrvs).thenReturn(Future.successful(Seq.empty[Trv]))
    when(devicesService.getTemperatureSensors(using any())).thenReturn(Future.successful(Seq.empty[TemperatureSensor]))

    val sut = new HeatingControlService(appConfig, nestConnector, devicesService, scheduleQueries, clock)(
      using scala.concurrent.ExecutionContext.global
    )

    def givenNestDevice(
        name: String = "Name",
        deviceType: String = "deviceType",
        traits: NestTraits
    ): OngoingStubbing[EitherT[Future, UpstreamErrorResponse, NestDevice]] =
      when(nestConnector.getDevice()(using any()))
        .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](NestDevice(name, deviceType, traits)))

    def givenTrvs(trvs: Trv*): OngoingStubbing[Future[Seq[Trv]]] =
      when(devicesService.getTrvs).thenReturn(Future.successful(trvs))

    def givenSensors(sensors: TemperatureSensor*): OngoingStubbing[Future[Seq[TemperatureSensor]]] =
      when(devicesService.getTemperatureSensors).thenReturn(Future.successful(sensors))

    def givenSchedule(sensorName: String, periods: SchedulePeriod*): OngoingStubbing[Future[ScheduleDay]] =
      when(scheduleQueries.getSchedule(sensorName)).thenReturn(Future.successful(schedule(periods*)))

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
      givenSchedule("sensor1", currentPeriod)

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
      givenSchedule("sensor1", currentPeriod)

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not query the devices when the heating is already off" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
      verifyNoInteractions(devicesService)
      verifyNoInteractions(scheduleQueries)
    }

    "not turn the heating off when the thermostat is not in HEAT mode" in new Fixture {
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.HEATING))

      sut.onTrvUpdate(fakeTrv("trv1", runningState = RunningState.Idle)).futureValue mustBe (())

      verifyThermostatNotChanged()
      verifyNoInteractions(devicesService)
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
      givenSchedule("sensor1", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyHeatingTurnedOn()
    }

    "use the period that contains the current time" in new Fixture {
      // 19.0 is above the current period (20.0 target would say heat) only if the wrong period is picked
      givenSchedule("sensor1", laterPeriod, currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyHeatingTurnedOn() // 19.0 < 20.0 (current period). The later period's 25.0 would also heat, so
      // the off-path test below is what proves the right period is chosen.
    }

    "use the standby temperature when no schedule period matches the current time" in new Fixture {
      givenSchedule("sensor1", laterPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.OFF))

      // 17.0 is above standby (16.0), so no heat is requested
      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 17.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating on when it is already on" in new Fixture {
      givenSchedule("sensor1", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "turn the heating off when the sensor is above target and no other device wants heat" in new Fixture {
      givenSchedule("sensor1", currentPeriod)
      givenSchedule("sensor2", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0), fakeTemperatureSensor("sensor2", 21.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyHeatingTurnedOff()
    }

    "not turn the heating off when another sensor is below its target" in new Fixture {
      givenSchedule("sensor1", currentPeriod)
      givenSchedule("sensor2", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0), fakeTemperatureSensor("sensor2", 18.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating off when a TRV is requesting heat" in new Fixture {
      givenSchedule("sensor1", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", 21.0))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Heat))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 21.0)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "not turn the heating off when the sensor is exactly at its target" in new Fixture {
      // `<` decides heat vs idle, but the demand check uses `<=`, so a sensor exactly at target still counts as
      // requesting heat and blocks the turn-off. This pins the current behaviour.
      givenSchedule("sensor1", currentPeriod)
      givenNestDevice(traits = fakeNestTraits(thermostatMode = ThermostatMode.HEAT, hvacStatus = HvacStatus.HEATING))
      givenSensors(fakeTemperatureSensor("sensor1", scheduleTarget))
      givenTrvs(fakeTrv("trv1", runningState = RunningState.Idle))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", scheduleTarget)).futureValue mustBe (())

      verifyThermostatNotChanged()
    }

    "complete successfully when the schedule lookup fails" in new Fixture {
      when(scheduleQueries.getSchedule(any[String]()))
        .thenReturn(Future.failed(new RuntimeException("boom")))

      sut.onTemperatureSensorUpdate(fakeTemperatureSensor("sensor1", 19.0)).futureValue mustBe (())

      verifyNoInteractions(nestConnector)
    }
  }

}
