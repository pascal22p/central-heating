package filters

import java.util.UUID
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderNames

import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{ any, eq => eqTo }
import org.mockito.Mockito

import config.ErrorHandler
import models.Attrs
import testUtils.BaseSpec

class RequestAttrFilterSpec extends BaseSpec {

  implicit lazy val materializer: Materializer =
    app.materializer

  implicit val ec: ExecutionContext =
    ExecutionContext.global

  lazy val errorHandler: ErrorHandler =
    Mockito.mock(classOf[ErrorHandler])

  lazy val sut =
    new RequestAttrFilter(errorHandler, materializer)

  private val existingRequestId =
    "12345678-1234-1234-1234-123456789012"

  private val existingSessionId =
    "87654321-4321-4321-4321-210987654321"

  "RequestAttrFilter" should {

    "generate a request ID when one is not provided" in {
      var capturedRequestId: Option[String] = None

      val next: RequestHeader => scala.concurrent.Future[Result] =
        request => {
          capturedRequestId = request.attrs.get(Attrs.RequestId)
          scala.concurrent.Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut.apply(next)(request).futureValue

      status(scala.concurrent.Future.successful(result)) mustBe OK

      val requestId =
        capturedRequestId.getOrElse(
          fail("Expected request ID to be available to downstream request")
        )

      UUID.fromString(requestId)

      result.header.headers.get(HeaderNames.xRequestId) mustBe Some(requestId)
    }

    "reuse an existing request ID" in {
      var capturedRequestId: Option[String] = None

      val next: RequestHeader => scala.concurrent.Future[Result] =
        request => {
          capturedRequestId = request.attrs.get(Attrs.RequestId)
          scala.concurrent.Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withHeaders(HeaderNames.xRequestId -> existingRequestId)
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut.apply(next)(request).futureValue

      capturedRequestId mustBe Some(existingRequestId)
      result.header.headers.get(HeaderNames.xRequestId) mustBe Some(existingRequestId)
    }

    "retrieve the session ID from the request session" in {
      var capturedSessionId: Option[String] = None

      val next: RequestHeader => scala.concurrent.Future[Result] =
        request => {
          capturedSessionId = request.attrs.get(Attrs.SessionId)
          scala.concurrent.Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withSession("sessionId" -> existingSessionId)

      sut.apply(next)(request).futureValue

      capturedSessionId mustBe Some(existingSessionId)
    }

    "make both request ID and session ID available as downstream request attributes" in {
      var capturedRequest: Option[RequestHeader] = None

      val next: RequestHeader => scala.concurrent.Future[Result] =
        request => {
          capturedRequest = Some(request)
          scala.concurrent.Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withHeaders(HeaderNames.xRequestId -> existingRequestId)
          .withSession("sessionId" -> existingSessionId)

      sut.apply(next)(request).futureValue

      val enrichedRequest =
        capturedRequest.getOrElse(
          fail("Expected downstream request to be captured")
        )

      enrichedRequest.attrs.get(Attrs.RequestId) mustBe Some(existingRequestId)
      enrichedRequest.attrs.get(Attrs.SessionId) mustBe Some(existingSessionId)
    }

    "add request ID and session ID to the response headers" in {
      val next: RequestHeader => scala.concurrent.Future[Result] =
        _ => scala.concurrent.Future.successful(Results.Ok)

      val request =
        FakeRequest(GET, "/")
          .withHeaders(HeaderNames.xRequestId -> existingRequestId)
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut.apply(next)(request).futureValue

      result.header.headers.get(HeaderNames.xRequestId) mustBe Some(existingRequestId)
      result.header.headers.get(HeaderNames.xSessionId) mustBe None
    }

    "replace existing request ID and session ID response headers" in {
      val oldRequestId = "old-request-id"
      val oldSessionId = "old-session-id"

      val next: RequestHeader => scala.concurrent.Future[Result] =
        _ =>
          scala.concurrent.Future.successful(
            Results.Ok.withHeaders(
              HeaderNames.xRequestId -> oldRequestId,
              HeaderNames.xSessionId -> oldSessionId
            )
          )

      val request =
        FakeRequest(GET, "/")
          .withHeaders(HeaderNames.xRequestId -> existingRequestId)
          .withSession("sessionId" -> existingSessionId)

      val result =
        sut.apply(next)(request).futureValue

      result.header.headers.get(HeaderNames.xRequestId) mustBe Some(existingRequestId)
      result.header.headers.get(HeaderNames.xSessionId) mustBe None
    }

    "throw an IllegalStateException when the session ID is missing" in {
      val next: RequestHeader => scala.concurrent.Future[Result] =
        _ => scala.concurrent.Future.successful(Results.Ok)

      val request =
        FakeRequest(GET, "/")

      intercept[IllegalStateException] {
        sut.apply(next)(request)
      }.getMessage mustBe "Session ID missing from request"
    }

    "update the incoming request headers with the request ID and session ID" in {
      var capturedRequest: Option[RequestHeader] = None

      val oldRequestId = "old-request-id"
      val oldSessionId = "old-session-id"

      val next: RequestHeader => Future[Result] =
        request => {
          capturedRequest = Some(request)
          Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .withHeaders(
            HeaderNames.xRequestId -> oldRequestId,
            HeaderNames.xSessionId -> oldSessionId
          )
          .withSession("sessionId" -> existingSessionId)

      sut.apply(next)(request).futureValue

      val enrichedRequest =
        capturedRequest.getOrElse(
          fail("Expected enriched request to be passed downstream")
        )

      enrichedRequest.headers.get(HeaderNames.xRequestId) mustBe Some(oldRequestId)
      enrichedRequest.headers.get(HeaderNames.xSessionId) mustBe Some(existingSessionId)

      enrichedRequest.headers.headers.count(
        _._1.equalsIgnoreCase(HeaderNames.xRequestId)
      ) mustBe 1

      enrichedRequest.headers.headers.count(
        _._1.equalsIgnoreCase(HeaderNames.xSessionId)
      ) mustBe 1
    }

    "delegate non-fatal exceptions to the error handler with the enriched request" in {
      val exception = new RuntimeException("boom")

      val request =
        FakeRequest(GET, "/")
          .withHeaders(HeaderNames.xRequestId -> existingRequestId)
          .withSession("sessionId" -> existingSessionId)

      val next: RequestHeader => Future[Result] =
        _ => Future.failed(exception)

      Mockito.reset(errorHandler)

      Mockito
        .when(
          errorHandler.onServerError(
            any[RequestHeader](),
            any[Throwable]()
          )
        )
        .thenReturn(Future.successful(Results.InternalServerError))

      val result =
        sut.apply(next)(request).futureValue

      status(Future.successful(result)) mustBe INTERNAL_SERVER_ERROR

      val requestCaptor =
        org.mockito.ArgumentCaptor.forClass(classOf[RequestHeader])

      Mockito
        .verify(errorHandler)
        .onServerError(
          requestCaptor.capture(),
          eqTo(exception)
        )

      val enrichedRequest = requestCaptor.getValue

      enrichedRequest.attrs.get(Attrs.RequestId) mustBe Some(existingRequestId)
      enrichedRequest.attrs.get(Attrs.SessionId) mustBe Some(existingSessionId)

      enrichedRequest.headers.get(HeaderNames.xRequestId) mustBe Some(existingRequestId)
      enrichedRequest.headers.get(HeaderNames.xSessionId) mustBe Some(existingSessionId)
    }
  }
}
