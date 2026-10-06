package controllers.admin

import java.time.{ LocalDateTime, ZoneOffset }
import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.Logging
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.DurationInt

import uk.gov.hmrc.http.{ HeaderCarrier, UpstreamErrorResponse }
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import anorm.SQL
import anorm.SqlParser.scalar
import cats.data.EitherT

@Singleton
class AdminController @Inject() (
    authJourney: AuthJourney,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport
    with Logging {

  def index: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    Future.successful(Ok(""))
  }

}
