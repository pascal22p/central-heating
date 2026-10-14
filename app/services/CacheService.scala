package services

import play.api.libs.json.*
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }

import com.google.inject.Inject

import cats.data.EitherT
import queries.CacheQueries

class CacheService @Inject() (
    cacheQueries: CacheQueries
)(implicit ec: ExecutionContext)
    extends Logging {

  def deleteFromCache(cacheKey: String): Future[Unit] =
    cacheQueries.deleteFromCache(cacheKey).map(_ => ())

  def cache[L, A: Format](
      key: String
  )(f: => EitherT[Future, L, A]): EitherT[Future, L, A] = {
    def fetchAndCache: EitherT[Future, L, A] =
      for {
        result <- f
        _      <- EitherT.liftF(cacheQueries.UpsertInCache[A](key, result))
      } yield result

    def readAndUpdate: EitherT[Future, L, A] =
      EitherT(
        cacheQueries
          .getFromCache(key)
          .flatMap {
            case None =>
              fetchAndCache.value
            case Some(value) =>
              Future.successful(Right(value))
          }
      )

    readAndUpdate
  }

}
