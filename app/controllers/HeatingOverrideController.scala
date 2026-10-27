package controllers

import javax.inject.{ Inject, Singleton }
import play.api.i18n.I18nSupport
import play.api.mvc.*
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import actions.AuthJourney
import models.HeatingOverride
import queries.HeatingOverrideQueries
import services.HeatingControlService
import views.html.HeatingOverrideView

@Singleton
final class HeatingOverrideController @Inject() (
    cc: ControllerComponents,
    heatingOverrideQueries: HeatingOverrideQueries,
    heatingControlService: HeatingControlService,
    authJourney: AuthJourney,
    heatingOverrideView: HeatingOverrideView
)(using ec: ExecutionContext)
    extends AbstractController(cc)
    with I18nSupport {

  def show: Action[AnyContent] = authJourney.guestAccess.async { implicit request =>
    heatingOverrideQueries.getOverrideState.map { current =>
      Ok(heatingOverrideView(current))
    }
  }

  // intentionally not using admin right to be more easily accessible locally.
  // From the Internet the entire app is restricted using nginx auth request
  def save: Action[AnyContent] = authJourney.guestAccess.async { implicit request =>
    given HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    def field(name: String): Option[String] =
      request.body.asFormUrlEncoded.flatMap(_.get(name)).flatMap(_.headOption)

    // An empty duration means "until switched back to automatic"; anything else must be one of the offered options.
    val duration: Either[String, Option[Int]] = field("duration").map(_.trim).filter(_.nonEmpty) match {
      case None        => Right(None)
      case Some(value) =>
        value.toIntOption
          .filter(minutes => HeatingOverride.durationOptions.exists(_._1 == minutes))
          .map(minutes => Right(Some(minutes)))
          .getOrElse(Left("Choose one of the listed durations."))
    }

    val update: Option[Future[Unit]] = (field("mode"), duration) match {
      case (Some("auto"), _)        => Some(clear())
      case (Some("heat"), Right(d)) => Some(set(HeatingOverride.Heat, d))
      case (Some("idle"), Right(d)) => Some(set(HeatingOverride.Idle, d))
      case _                        => None
    }

    update match {
      case Some(done) =>
        done.map(_ => Redirect(controllers.routes.HeatingOverrideController.show))
      case None =>
        val message = duration.fold(identity, _ => "Choose heat, idle or automatic.")
        heatingOverrideQueries.getOverrideState.map { current =>
          BadRequest(heatingOverrideView(current, Some(message)))
        }
    }
  }

  private def set(mode: HeatingOverride, durationMinutes: Option[Int])(using HeaderCarrier): Future[Unit] =
    for {
      _ <- heatingOverrideQueries.setOverride(mode, durationMinutes)
      _ <- heatingControlService.applyOverride(mode)
    } yield ()

  private def clear()(using HeaderCarrier): Future[Unit] =
    for {
      _ <- heatingOverrideQueries.clearOverride()
      _ <- heatingControlService.applyAutomatic()
    } yield ()

}
