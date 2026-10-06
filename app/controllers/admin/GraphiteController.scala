package controllers.admin

import javax.inject.Inject
import play.api.mvc.*
import scala.concurrent.ExecutionContext

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import cats.data.EitherT
import connectors.{ GraphiteConnector, NestConnector }

class GraphiteController @Inject() (
    authJourney: AuthJourney,
    nestConnector: NestConnector,
    graphiteConnector: GraphiteConnector,
    cc: MessagesControllerComponents
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def pushNest: Action[AnyContent] =
    authJourney.authWithAdminRight.async { implicit request =>
      implicit val hc: HeaderCarrier =
        HeaderCarrierConverter.fromRequest(request)

      nestConnector
        .getDevice()
        .flatMap(device => EitherT.liftF(graphiteConnector.send(device)))
        .fold(
          error => InternalServerError(error.message),
          _ => Redirect(controllers.routes.NestController.index())
        )
    }
}
