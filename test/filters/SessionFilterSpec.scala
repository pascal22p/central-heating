package filters

import java.util.UUID
import play.api.libs.streams.Accumulator
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import org.apache.pekko.stream.Materializer

import models.Attrs
import testUtils.BaseSpec

class SessionFilterSpec extends BaseSpec {

  implicit lazy val materializer: Materializer =
    app.materializer

  lazy val sut =
    new SessionFilter()(using mat = materializer, ec = scala.concurrent.ExecutionContext.global)

  "SessionFilter" should {

    "create a new session ID and make it available to the downstream action" in {
      var capturedSessionId: Option[String] = None

      val next: EssentialAction =
        EssentialAction { request =>
          capturedSessionId = request.attrs.get(Attrs.SessionId)
          Accumulator.done(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")

      val result =
        sut
          .apply(next)(request)
          .run()

      status(result) mustBe OK

      val sessionId =
        capturedSessionId.getOrElse(
          fail("Expected session ID to be available to downstream action")
        )

      UUID.fromString(sessionId)

      result.futureValue.session(using request).get("sessionId") mustBe Some(sessionId)
    }

    "reuse an existing session ID" in {
      val existingSessionId =
        "12345678-1234-1234-1234-123456789012"

      var capturedSessionId: Option[String] = None

      val next: EssentialAction =
        EssentialAction { request =>
          capturedSessionId = request.attrs.get(Attrs.SessionId)
          Accumulator.done(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut
          .apply(next)(request)
          .run()

      status(result) mustBe OK
      capturedSessionId mustBe Some(existingSessionId)
      result.futureValue.session(using request).get("sessionId") mustBe Some(existingSessionId)
    }

    "expose the session ID through sessionId" in {
      val existingSessionId =
        "12345678-1234-1234-1234-123456789012"

      var capturedRequest: Option[RequestHeader] = None

      val next: EssentialAction =
        EssentialAction { request =>
          capturedRequest = Some(request)
          Accumulator.done(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withSession("sessionId" -> existingSessionId)

      sut
        .apply(next)(request)
        .run()
        .futureValue

      sut.sessionId(capturedRequest.get) mustBe Some(existingSessionId)
    }

    "not replace an existing session ID in the response" in {
      val existingSessionId =
        "12345678-1234-1234-1234-123456789012"

      val next: EssentialAction =
        EssentialAction { _ =>
          Accumulator.done(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut
          .apply(next)(request)
          .run()

      status(result) mustBe OK
      result.futureValue.session(using request).get("sessionId") mustBe Some(existingSessionId)
    }

    "use the same new session ID for the downstream action and response" in {
      var capturedSessionId: Option[String] = None

      val next: EssentialAction =
        EssentialAction { request =>
          capturedSessionId = request.attrs.get(Attrs.SessionId)
          Accumulator.done(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")

      val result =
        sut
          .apply(next)(request)
          .run()
          .futureValue
      val downstreamSessionId =
        capturedSessionId.getOrElse(
          fail("Expected session ID to be available to downstream action")
        )

      val responseSessionId =
        result
          .session(using request)
          .get("sessionId")
          .getOrElse(
            fail("Expected session ID to be stored in response session")
          )

      downstreamSessionId mustBe responseSessionId
    }
  }
}
