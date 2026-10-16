package queries

import anorm.SQL
import models.HeatingOverride
import testUtils.MariadbHelper

class HeatingOverrideQueriesSpec extends MariadbHelper {

  private lazy val sut = app.injector.instanceOf[HeatingOverrideQueries]

  "HeatingOverrideQueries" should {

    "return no override when nothing is stored" in {
      sut.getOverride.futureValue mustBe None
    }

    "store and read back a heat override" in {
      sut.setOverride(HeatingOverride.Heat).futureValue
      sut.getOverride.futureValue mustBe Some(HeatingOverride.Heat)
    }

    "replace an existing override rather than adding a second row" in {
      sut.setOverride(HeatingOverride.Heat).futureValue
      sut.setOverride(HeatingOverride.Idle).futureValue
      sut.getOverride.futureValue mustBe Some(HeatingOverride.Idle)
    }

    "clear the override" in {
      sut.setOverride(HeatingOverride.Idle).futureValue
      sut.clearOverride().futureValue
      sut.getOverride.futureValue mustBe None
    }

    "have no expiry when no duration is given" in {
      sut.setOverride(HeatingOverride.Heat).futureValue
      sut.getOverrideState.futureValue.map(_.expiresAt) mustBe Some(None)
    }

    "return an override that has not expired yet, with its expiry" in {
      sut.setOverride(HeatingOverride.Heat, Some(60)).futureValue

      val state = sut.getOverrideState.futureValue
      state.map(_.mode) mustBe Some(HeatingOverride.Heat)
      state.flatMap(_.expiresAt).isDefined mustBe true
      sut.getOverride.futureValue mustBe Some(HeatingOverride.Heat)
    }

    "ignore an override that has expired" in {
      sut.setOverride(HeatingOverride.Heat, Some(60)).futureValue
      db.withConnection { implicit conn =>
        SQL("UPDATE heating_override SET expires_at = CURRENT_TIMESTAMP - INTERVAL 1 MINUTE").execute()
      }

      sut.getOverride.futureValue mustBe None
      sut.getOverrideState.futureValue mustBe None
    }

    "replace the expiry when the override is set again without a duration" in {
      sut.setOverride(HeatingOverride.Heat, Some(60)).futureValue
      sut.setOverride(HeatingOverride.Idle).futureValue

      sut.getOverrideState.futureValue.map(_.expiresAt) mustBe Some(None)
    }
  }
}
