package actions

import javax.inject.Inject
import play.api.mvc.{ ActionBuilder, AnyContent }

import com.google.inject.ImplementedBy

import models.AuthenticatedRequest

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  val authWithAdminRight: ActionBuilder[AuthenticatedRequest, AnyContent]

  val guestAccess: ActionBuilder[AuthenticatedRequest, AnyContent]
}

class AuthJourneyImpl @Inject() (
    authAction: AuthAction,
    adminFilter: AdminFilter
) extends AuthJourney {

  val authWithAdminRight: ActionBuilder[AuthenticatedRequest, AnyContent] =
    authAction.andThen(adminFilter)

  val guestAccess: ActionBuilder[AuthenticatedRequest, AnyContent] = authAction
}
