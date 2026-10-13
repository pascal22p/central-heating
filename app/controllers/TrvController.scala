package controllers

import javax.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc.{ Action, AnyContent, BaseController, ControllerComponents }
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import services.DevicesService
import views.html.TrvsView

class TrvController @Inject() (
    val controllerComponents: ControllerComponents,
    authAction: AuthJourney,
    devicesService: DevicesService,
    trvsView: TrvsView
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index(): Action[AnyContent] = authAction.guestAccess.async { implicit authenticatedRequest =>
    given HeaderCarrier = HeaderCarrierConverter.fromRequest(authenticatedRequest)

    devicesService.getTrvs.map { trvs =>
      Ok(trvsView(trvs))
    }
  }
}
