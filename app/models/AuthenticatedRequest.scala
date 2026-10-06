package models

import play.api.mvc.{ Request, WrappedRequest }
import play.api.MarkerContext

final case class AuthenticatedRequest[A](
    request: Request[A],
    localSession: Session
)(implicit val markerContext: MarkerContext)
    extends WrappedRequest[A](request)
