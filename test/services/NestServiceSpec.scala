package services

import java.time.{ Clock, Instant, ZoneOffset }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration.*
import scala.concurrent.ExecutionContext.global

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.stubbing.OngoingStubbing
import org.mockito.ArgumentMatchers.{ any, anyDouble, eq => eqTo }
import org.mockito.Mockito.*
import org.scalatestplus.mockito.MockitoSugar

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.{ HeatingOverride, ThermostatChange, ThermostatState }
import models.nest.{ HvacStatus, NestDevice, ThermostatMode }
import queries.{ HeatingOverrideQueries, ThermostatStateQueries }
import testUtils.BaseSpec

class NestServiceSpec extends BaseSpec with MockitoSugar {

  given HeaderCarrier    = HeaderCarrier()
  given ExecutionContext = global

  private val activeTemperature  = 22.0
  private val standbyTemperature = 16.0
  private val minimumOn          = 10.minutes
  private val minimumOff         = 5.minutes

  private class Fixture {
    val appConfig: AppConfig                      = mock[AppConfig]
    val nestConnector: NestConnector              = mock[NestConnector]
    val overrideQueries: HeatingOverrideQueries   = mock[HeatingOverrideQueries]
    val thermostatQueries: ThermostatStateQueries = mock[ThermostatStateQueries]

    val now: Instant = Instant.parse("2026-01-01T12:00:00Z")
    val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)

    when(appConfig.heatingDemandActiveTemperatureCelsius).thenReturn(activeTemperature)
    when(appConfig.heatingDemandStandbyTemperatureCelsius).thenReturn(standbyTemperature)
    when(appConfig.heatingDemandMinimumOn).thenReturn(minimumOn)
    when(appConfig.heatingDemandMinimumOff).thenReturn(minimumOff)
    when(overrideQueries.getOverride).thenReturn(Future.successful(None))
    when(thermostatQueries.get(any[String]())).thenReturn(Future.successful(None))
    when(thermostatQueries.set(any[String](), any[ThermostatState](), any[Instant]())).thenReturn(Future.unit)
    when(nestConnector.setTemperature(anyDouble())(using any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](()))

    val sut = new NestService(appConfig, nestConnector, overrideQueries, thermostatQueries, clock)

    def givenThermostat(
        thermostatMode: ThermostatMode = ThermostatMode.HEAT,
        hvacStatus: HvacStatus
    ): OngoingStubbing[EitherT[Future, UpstreamErrorResponse, NestDevice]] =
      when(nestConnector.getDevice()(using any()))
        .thenReturn(
          EitherT.rightT[Future, UpstreamErrorResponse](
            NestDevice("Name", "deviceType", fakeNestTraits(thermostatMode = thermostatMode, hvacStatus = hvacStatus))
          )
        )

    def givenOverride(mode: HeatingOverride): OngoingStubbing[Future[Option[HeatingOverride]]] =
      when(overrideQueries.getOverride).thenReturn(Future.successful(Some(mode)))

    /** The thermostat was last put in `state`, `ago` before now. */
    def givenLastChange(
        state: ThermostatState,
        ago: FiniteDuration
    ): OngoingStubbing[Future[Option[ThermostatChange]]] =
      when(thermostatQueries.get("Name"))
        .thenReturn(Future.successful(Some(ThermostatChange(state, now.minusSeconds(ago.toSeconds)))))

    def verifyTurnedOn(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(activeTemperature))(using any())

    def verifyTurnedOff(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(standbyTemperature))(using any())

    def verifyNotChanged(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector, never()).setTemperature(anyDouble())(using any())

    def verifyNothingStored(): Future[Unit] =
      verify(thermostatQueries, never()).set(any[String](), any[ThermostatState](), any[Instant]())
  }

  "NestService.turnOn" should {

    "turn the heating on when it is off" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "store the new state with the current time" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verify(thermostatQueries).set(eqTo("Name"), eqTo(ThermostatState.Heat), eqTo(now))
    }

    "do nothing when the heating is already on" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyNotChanged()
      verifyNothingStored()
    }

    "do nothing when the thermostat is not in HEAT mode" in new Fixture {
      givenThermostat(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.OFF)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "wait when the heating was turned off less than the minimum off time ago" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)
      givenLastChange(ThermostatState.Idle, 2.minutes)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyNotChanged()
      verifyNothingStored()
    }

    "turn the heating on when it was turned off longer than the minimum off time ago" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)
      givenLastChange(ThermostatState.Idle, 6.minutes)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "not wait when the last recorded change was to heat" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)
      givenLastChange(ThermostatState.Heat, 1.minute)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "not store anything when the thermostat refuses the change" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)
      when(nestConnector.setTemperature(anyDouble())(using any()))
        .thenReturn(EitherT.leftT[Future, Unit](UpstreamErrorResponse("boom", 500)))

      sut.turnOn("test").value.futureValue mustBe Left(UpstreamErrorResponse("boom", 500))

      verifyNothingStored()
    }

    "turn the heating off instead when the override is idle" in new Fixture {
      givenOverride(HeatingOverride.Idle)
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }

    "turn the heating on when the override is heat, whatever the time since the last change" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenThermostat(hvacStatus = HvacStatus.OFF)
      givenLastChange(ThermostatState.Idle, 1.minute)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }
  }

  "NestService.turnOff" should {

    "turn the heating off when it is on" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }

    "store the new state with the current time" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verify(thermostatQueries).set(eqTo("Name"), eqTo(ThermostatState.Idle), eqTo(now))
    }

    "do nothing when the heating is already off" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyNotChanged()
      verifyNothingStored()
    }

    "do nothing when the thermostat is not in HEAT mode" in new Fixture {
      givenThermostat(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.HEATING)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "wait when the heating was turned on less than the minimum on time ago" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)
      givenLastChange(ThermostatState.Heat, 9.minutes)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyNotChanged()
      verifyNothingStored()
    }

    "turn the heating off when it was turned on longer than the minimum on time ago" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)
      givenLastChange(ThermostatState.Heat, 11.minutes)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }

    "not wait when the last recorded change was to idle" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)
      givenLastChange(ThermostatState.Idle, 1.minute)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }

    "turn the heating on instead when the override is heat" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "turn the heating off when the override is idle, whatever the time since the last change" in new Fixture {
      givenOverride(HeatingOverride.Idle)
      givenThermostat(hvacStatus = HvacStatus.HEATING)
      givenLastChange(ThermostatState.Heat, 1.minute)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }
  }
}
