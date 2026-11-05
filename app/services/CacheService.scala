package services

import play.api.libs.json.*
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import com.google.inject.Inject

import cats.data.EitherT
import models.LoggingWithRequest
import queries.CacheQueries

class CacheService @Inject() (
    cacheQueries: CacheQueries
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  def deleteFromCache(cacheKey: String): Future[Unit] =
    cacheQueries.deleteFromCache(cacheKey).map(_ => ())

  def cache[L, A: Format](
      key: String
  )(f: => EitherT[Future, L, A])(implicit hc: HeaderCarrier): EitherT[Future, L, A] = {
    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _      <- EitherT.liftF(cacheQueries.UpsertInCache[A](key, result))
      } yield result

    def readAndUpdate(implicit hc: HeaderCarrier): EitherT[Future, L, A] =
      EitherT(
        cacheQueries
          .getFromCache(key)
          .flatMap {
            case None =>
              logger.debug(s"Cache miss for key $key")
              fetchAndCache.value
            case Some(value) =>
              logger.debug(s"Cache hit for key $key")
              Future.successful(Right(value))
          }
      )

    readAndUpdate
  }

}
