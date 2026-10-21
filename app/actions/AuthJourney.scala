package actions

import javax.inject.Inject
import play.api.mvc.{ ActionBuilder, AnyContent }

import com.google.inject.ImplementedBy

import models.AuthenticatedRequest

@ImplementedBy(classOf[AuthJourneyImpl])
trait AuthJourney {
  val authWithAdminRight: ActionBuilder[AuthenticatedRequest, AnyContent]

  val guestAccess: ActionBuilder[AuthenticatedRequest, AnyContent]

  val apiAccess: ActionBuilder[AuthenticatedRequest, AnyContent]
}

class AuthJourneyImpl @Inject() (
    authAction: AuthAction,
    authApiAction: AuthApiAction,
    adminFilter: AdminFilter
) extends AuthJourney {

  val authWithAdminRight: ActionBuilder[AuthenticatedRequest, AnyContent] =
    authAction.andThen(adminFilter)

  val guestAccess: ActionBuilder[AuthenticatedRequest, AnyContent] = authAction

  val apiAccess: ActionBuilder[AuthenticatedRequest, AnyContent] = authApiAction
}
