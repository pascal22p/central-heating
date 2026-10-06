package actions

import java.time.LocalDateTime
import java.util.UUID
import play.api.mvc.*
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import scala.concurrent.{ ExecutionContext, Future }

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{ never, reset, times, verify, when }

import models.{ Attrs, AuthenticatedRequest, Session, SessionData }
import queries.SessionSqlQueries
import testUtils.BaseSpec

class AuthActionImplSpec extends BaseSpec {

  implicit lazy val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.global

  lazy val mockSqlQueries: SessionSqlQueries =
    mock[SessionSqlQueries]

  lazy val sut =
    new AuthActionImpl(
      mockSqlQueries,
      stubMessagesControllerComponents()
    )

  protected override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSqlQueries)
  }

  "AuthActionImpl" should {

    "fail when the session ID is missing from the request" in {
      val request =
        FakeRequest(GET, "/")

      val block: AuthenticatedRequest[AnyContent] => Future[Result] =
        _ => Future.successful(Results.Ok)

      intercept[IllegalStateException] {
        await(
          sut.invokeBlock(request, block)
        )
      }.getMessage mustBe "Session ID missing from request"

      verify(mockSqlQueries, never()).getSessionData(any[String])
      verify(mockSqlQueries, never()).sessionKeepAlive(any[String])
      verify(mockSqlQueries, never()).putSessionData(any[Session])
    }

    "use an existing session and keep it alive" in {
      val sessionId =
        UUID.randomUUID().toString

      val existingSession =
        Session(
          sessionId,
          SessionData(None),
          LocalDateTime.now()
        )

      when(mockSqlQueries.getSessionData(sessionId))
        .thenReturn(Future.successful(Some(existingSession)))

      when(mockSqlQueries.sessionKeepAlive(sessionId))
        .thenReturn(Future.successful(1))

      var authenticatedRequest: Option[AuthenticatedRequest[AnyContent]] = None

      val block: AuthenticatedRequest[AnyContent] => Future[Result] =
        request => {
          authenticatedRequest = Some(request)
          Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .addAttr(Attrs.SessionId, sessionId)

      val result =
        sut.invokeBlock(request, block)

      status(result) mustBe OK

      authenticatedRequest mustBe defined
      authenticatedRequest.get.request mustBe request

      verify(mockSqlQueries, times(1))
        .getSessionData(sessionId)

      verify(mockSqlQueries, times(1))
        .sessionKeepAlive(sessionId)

      verify(mockSqlQueries, never())
        .putSessionData(org.mockito.ArgumentMatchers.any[Session])
    }

    "create and persist a new session when one does not exist" in {
      val sessionId =
        UUID.randomUUID().toString

      when(mockSqlQueries.getSessionData(sessionId))
        .thenReturn(Future.successful(None))

      when(mockSqlQueries.putSessionData(any[Session]))
        .thenReturn(Future.successful(Some("1")))

      var authenticatedRequest: Option[AuthenticatedRequest[AnyContent]] = None

      val block: AuthenticatedRequest[AnyContent] => Future[Result] =
        request => {
          authenticatedRequest = Some(request)
          Future.successful(Results.Ok)
        }

      val request =
        FakeRequest(GET, "/")
          .addAttr(Attrs.SessionId, sessionId)

      val result =
        sut.invokeBlock(request, block)

      status(result) mustBe OK

      authenticatedRequest mustBe defined
      authenticatedRequest.get.request mustBe request

      val sessionCaptor =
        ArgumentCaptor.forClass(classOf[Session])

      verify(mockSqlQueries, times(1))
        .putSessionData(sessionCaptor.capture())

      val createdSession: Session =
        sessionCaptor.getValue

      createdSession.sessionId mustBe sessionId
      createdSession.sessionData mustBe SessionData(None)

      verify(mockSqlQueries, times(1))
        .getSessionData(sessionId)

      verify(mockSqlQueries, never())
        .sessionKeepAlive(sessionId)
    }

    "pass the result returned by the authenticated block" in {
      val sessionId =
        UUID.randomUUID().toString

      val existingSession =
        Session(
          sessionId,
          SessionData(None),
          LocalDateTime.now()
        )

      when(mockSqlQueries.getSessionData(sessionId))
        .thenReturn(Future.successful(Some(existingSession)))

      when(mockSqlQueries.sessionKeepAlive(sessionId))
        .thenReturn(Future.successful(1))

      val block: AuthenticatedRequest[AnyContent] => Future[Result] =
        _ => Future.successful(Results.Created)

      val request =
        FakeRequest(GET, "/")
          .addAttr(Attrs.SessionId, sessionId)

      val result =
        sut.invokeBlock(request, block)

      status(result) mustBe CREATED
    }
  }
}
