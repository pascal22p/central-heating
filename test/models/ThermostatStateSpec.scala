package models

import org.scalatestplus.play.PlaySpec

class ThermostatStateSpec extends PlaySpec {

  "ThermostatState.fromDbValue" should {

    "round-trip the database values" in {
      ThermostatState.values.toSeq.map(s => ThermostatState.fromDbValue(s.dbValue)) mustBe
        ThermostatState.values.toSeq.map(Some(_))
    }

    "ignore unknown values" in {
      ThermostatState.fromDbValue("boost") mustBe None
    }
  }
}
