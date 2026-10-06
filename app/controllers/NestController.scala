package controllers

import javax.inject.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.{ AuthAction, AuthJourney }
import connectors.NestConnector
import controllers.routes
import views.html.NestView

@Singleton
class NestController @Inject() (
    val controllerComponents: ControllerComponents,
    nestConnector: NestConnector,
    authAction: AuthJourney,
    nestView: NestView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    nestConnector.getDevice().value.map {
      case Right(device) =>
        Ok(nestView(device))

      case Left(error) =>
        InternalServerError(
          s"Unable to read Nest: ${error.message}"
        )
    }
  }
}
