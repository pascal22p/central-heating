package queries

import java.time.Instant

import models.{ HeatDemand, SensorHeatDemand }
import testUtils.MariadbHelper

class SensorHeatDemandQueriesSpec extends MariadbHelper {

  private lazy val sut = app.injector.instanceOf[SensorHeatDemandQueries]

  private val earlier = Instant.parse("2026-01-01T10:00:00Z")
  private val later   = Instant.parse("2026-01-01T12:30:15Z")

  "SensorHeatDemandQueries" should {

    "return nothing for a sensor that has never been stored" in {
      sut.get("sensor1").futureValue mustBe None
    }

    "store and read back a demand with the time it changed" in {
      sut.set("sensor1", HeatDemand.Heat, earlier).futureValue

      sut.get("sensor1").futureValue mustBe Some(SensorHeatDemand(HeatDemand.Heat, earlier))
    }

    "replace the demand and the time of an existing sensor rather than adding a second row" in {
      sut.set("sensor1", HeatDemand.Heat, earlier).futureValue
      sut.set("sensor1", HeatDemand.Idle, later).futureValue

      sut.get("sensor1").futureValue mustBe Some(SensorHeatDemand(HeatDemand.Idle, later))
    }

    "keep the demand of each sensor separate" in {
      sut.set("sensor1", HeatDemand.Heat, earlier).futureValue
      sut.set("sensor2", HeatDemand.Idle, later).futureValue

      sut.get("sensor1").futureValue mustBe Some(SensorHeatDemand(HeatDemand.Heat, earlier))
      sut.get("sensor2").futureValue mustBe Some(SensorHeatDemand(HeatDemand.Idle, later))
    }
  }
}
