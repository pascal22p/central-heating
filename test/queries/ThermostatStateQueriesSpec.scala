package queries

import java.time.Instant

import models.{ ThermostatChange, ThermostatState }
import testUtils.MariadbHelper

class ThermostatStateQueriesSpec extends MariadbHelper {

  private lazy val sut = app.injector.instanceOf[ThermostatStateQueries]

  private val earlier = Instant.parse("2026-01-01T10:00:00Z")
  private val later   = Instant.parse("2026-01-01T12:30:15Z")

  "ThermostatStateQueries" should {

    "return nothing for a thermostat that has never been switched" in {
      sut.get("thermostat1").futureValue mustBe None
    }

    "store and read back a state with the time it changed" in {
      sut.set("thermostat1", ThermostatState.Heat, earlier).futureValue

      sut.get("thermostat1").futureValue mustBe Some(ThermostatChange(ThermostatState.Heat, earlier))
    }

    "replace the state and the time of an existing thermostat rather than adding a second row" in {
      sut.set("thermostat1", ThermostatState.Heat, earlier).futureValue
      sut.set("thermostat1", ThermostatState.Idle, later).futureValue

      sut.get("thermostat1").futureValue mustBe Some(ThermostatChange(ThermostatState.Idle, later))
    }

    "keep the original time when the state is unchanged" in {
      sut.set("thermostat1", ThermostatState.Heat, earlier).futureValue
      sut.set("thermostat1", ThermostatState.Heat, later).futureValue

      sut.get("thermostat1").futureValue mustBe Some(ThermostatChange(ThermostatState.Heat, earlier))
    }

    "keep the state of each thermostat separate" in {
      sut.set("thermostat1", ThermostatState.Heat, earlier).futureValue
      sut.set("thermostat2", ThermostatState.Idle, later).futureValue

      sut.get("thermostat1").futureValue mustBe Some(ThermostatChange(ThermostatState.Heat, earlier))
      sut.get("thermostat2").futureValue mustBe Some(ThermostatChange(ThermostatState.Idle, later))
    }
  }
}
