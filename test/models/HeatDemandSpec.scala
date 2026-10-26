package models

import org.scalatestplus.play.PlaySpec

class HeatDemandSpec extends PlaySpec {

  private val target     = 20.0
  private val hysteresis = 0.5

  "HeatDemand.fromDbValue" should {

    "round-trip the database values" in {
      HeatDemand.values.toSeq.map(d => HeatDemand.fromDbValue(d.dbValue)) mustBe HeatDemand.values.toSeq.map(Some(_))
    }

    "ignore unknown values" in {
      HeatDemand.fromDbValue("boost") mustBe None
    }
  }

  "HeatDemand.next" should {

    "start heating when idle and below the lower bound" in {
      HeatDemand.next(HeatDemand.Idle, 19.4, target, hysteresis) mustBe HeatDemand.Heat
    }

    "stay idle when idle and inside the band" in {
      HeatDemand.next(HeatDemand.Idle, 19.5, target, hysteresis) mustBe HeatDemand.Idle
      HeatDemand.next(HeatDemand.Idle, 20.2, target, hysteresis) mustBe HeatDemand.Idle
    }

    "stay idle when idle and above the band" in {
      HeatDemand.next(HeatDemand.Idle, 21.0, target, hysteresis) mustBe HeatDemand.Idle
    }

    "keep heating when heating and inside the band" in {
      HeatDemand.next(HeatDemand.Heat, 19.5, target, hysteresis) mustBe HeatDemand.Heat
      HeatDemand.next(HeatDemand.Heat, 20.4, target, hysteresis) mustBe HeatDemand.Heat
    }

    "keep heating when heating and below the band" in {
      HeatDemand.next(HeatDemand.Heat, 18.0, target, hysteresis) mustBe HeatDemand.Heat
    }

    "stop heating when heating and the upper bound is reached" in {
      HeatDemand.next(HeatDemand.Heat, 20.5, target, hysteresis) mustBe HeatDemand.Idle
      HeatDemand.next(HeatDemand.Heat, 21.0, target, hysteresis) mustBe HeatDemand.Idle
    }

    "behave as a plain threshold when the hysteresis is zero" in {
      HeatDemand.next(HeatDemand.Idle, 19.9, target, 0.0) mustBe HeatDemand.Heat
      HeatDemand.next(HeatDemand.Heat, 20.0, target, 0.0) mustBe HeatDemand.Idle
    }
  }
}
