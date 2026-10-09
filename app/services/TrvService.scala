package services

import javax.inject.{ Inject, Singleton }
import play.api.Logging
import scala.concurrent.{ ExecutionContext, Future }

import cats.implicits.*
import config.AppConfig
import models.Trv
import queries.TrvQueries

@Singleton
class TrvService @Inject() (
    trvQueries: TrvQueries,
    appConfig: AppConfig,
    mqttPublisherService: MqttPublisherService
)(implicit ec: ExecutionContext)
    extends Logging {

  @SuppressWarnings(Array("org.wartremover.warts.ThreadSleep"))
  def getTrvs: Future[Seq[Trv]] = {
    val devicesConfig = appConfig.trvDevices
    trvQueries.getTrvs.flatMap { trvs =>
      devicesConfig
        .filterNot(device => trvs.exists(_.name == device))
        .traverse { device =>
          logger.info(s"Requesting TRV state for device: $device")
          for {
            _   <- mqttPublisherService.requestTrvState(device)
            _   <- Future.successful(Thread.sleep(5000))
            trv <- trvQueries.getTrv(device)
          } yield trv
        }
        .map(_.flatten ++ trvs)
    }
  }

}
