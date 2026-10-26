package services

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.ExecutionContext.global

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }

import org.mockito.ArgumentMatchers.{ any, anyDouble, eq => eqTo }
import org.mockito.Mockito.*
import org.mockito.stubbing.OngoingStubbing
import org.scalatestplus.mockito.MockitoSugar

import cats.data.EitherT
import config.AppConfig
import connectors.NestConnector
import models.HeatingOverride
import models.nest.{ HvacStatus, NestDevice, NestTraits, ThermostatMode }
import queries.HeatingOverrideQueries
import testUtils.BaseSpec

class NestServiceSpec extends BaseSpec with MockitoSugar {

  given HeaderCarrier    = HeaderCarrier()
  given ExecutionContext = global

  private val activeTemperature  = 22.0
  private val standbyTemperature = 16.0

  private class Fixture {
    val appConfig: AppConfig                    = mock[AppConfig]
    val nestConnector: NestConnector            = mock[NestConnector]
    val overrideQueries: HeatingOverrideQueries = mock[HeatingOverrideQueries]

    when(appConfig.heatingDemandActiveTemperatureCelsius).thenReturn(activeTemperature)
    when(appConfig.heatingDemandStandbyTemperatureCelsius).thenReturn(standbyTemperature)
    when(overrideQueries.getOverride).thenReturn(Future.successful(None))
    when(nestConnector.setTemperature(anyDouble())(using any()))
      .thenReturn(EitherT.rightT[Future, UpstreamErrorResponse](()))

    val sut = new NestService(appConfig, nestConnector, overrideQueries)

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

    def verifyTurnedOn(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(activeTemperature))(using any())

    def verifyTurnedOff(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector).setTemperature(eqTo(standbyTemperature))(using any())

    def verifyNotChanged(): EitherT[Future, UpstreamErrorResponse, Unit] =
      verify(nestConnector, never()).setTemperature(anyDouble())(using any())
  }

  "NestService.turnOn" should {

    "turn the heating on when it is off" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "do nothing when the heating is already on" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "do nothing when the thermostat is not in HEAT mode" in new Fixture {
      givenThermostat(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.OFF)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "turn the heating off instead when the override is idle" in new Fixture {
      givenOverride(HeatingOverride.Idle)
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOn("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }

    "turn the heating on when the override is heat" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenThermostat(hvacStatus = HvacStatus.OFF)

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

    "do nothing when the heating is already off" in new Fixture {
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "do nothing when the thermostat is not in HEAT mode" in new Fixture {
      givenThermostat(thermostatMode = ThermostatMode.OFF, hvacStatus = HvacStatus.HEATING)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyNotChanged()
    }

    "turn the heating on instead when the override is heat" in new Fixture {
      givenOverride(HeatingOverride.Heat)
      givenThermostat(hvacStatus = HvacStatus.OFF)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOn()
    }

    "turn the heating off when the override is idle" in new Fixture {
      givenOverride(HeatingOverride.Idle)
      givenThermostat(hvacStatus = HvacStatus.HEATING)

      sut.turnOff("test").value.futureValue mustBe Right(())

      verifyTurnedOff()
    }
  }
}
