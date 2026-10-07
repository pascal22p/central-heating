package controllers.admin

import javax.inject.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import connectors.NestConnector
import views.html.NestView

@Singleton
class NestAdminController @Inject() (
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

  def setTemperature(temperature: Double): Action[AnyContent] = authAction.authWithAdminRight.async {
    implicit authenticatedRequest =>
      implicit val hc: HeaderCarrier =
        HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)

      nestConnector
        .setTemperature(temperature)
        .fold(
          error => InternalServerError(error.message),
          _ => Ok("Temperature set successfully")
        )
  }

  def authorize(): Action[AnyContent] = authAction.authWithAdminRight {
    Redirect(
      nestConnector.authorizationUrl()
    )
  }

  def oauthCallback(
      code: String,
      state: String
  ): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc =
        uk.gov.hmrc.http.HeaderCarrier()

      nestConnector
        .handleCallback(code, state)
        .value
        .map {

          case Right(_) =>
            Redirect(controllers.routes.NestController.index())

          case Left(error) =>
            BadRequest(
              s"Nest authorisation failed: ${error.message}"
            )
        }
    }
}
