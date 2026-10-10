package connectors

import java.io.{ BufferedWriter, OutputStreamWriter }
import java.net.{ InetSocketAddress, Socket }
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Using }

import config.AppConfig
import models.{ LoggingWithRequest, NestDevice }

class GraphiteConnector @Inject() (
    appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends LoggingWithRequest {

  private val metricPrefix = appConfig.graphitePrefix

  def send(device: NestDevice): Future[Unit] =
    Future {
      val timestamp = System.currentTimeMillis() / 1000

      val optValues = Seq(
        device.traits.heatSetpointCelsius.map(value => metric("heat_setpoint_celsius", value, timestamp)),
        device.traits.coolSetpointCelsius.map(value => metric("cool_setpoint_celsius", value, timestamp))
      ).flatten

      val metrics = Seq(
        metric("temperature_celsius", device.traits.temperatureCelsius, timestamp),
        metric("humidity_percent", device.traits.humidityPercent, timestamp),
        metric("eco_heat_celsius", device.traits.ecoHeatCelsius, timestamp),
        metric("eco_cool_celsius", device.traits.ecoCoolCelsius, timestamp),
        binaryMetric(
          "thermostat_mode",
          device.traits.thermostatMode,
          "HEAT",
          "OFF",
          timestamp
        ),
        binaryMetric(
          "hvac_status",
          device.traits.hvacStatus,
          "HEATING",
          "OFF",
          timestamp
        ),
        binaryMetric(
          "eco_mode",
          device.traits.ecoMode,
          "MANUAL_ECO",
          "OFF",
          timestamp
        )
      ) ++ optValues

      if (metrics.nonEmpty) {
        logger.info(
          s"Sending ${metrics.size} metrics to Graphite at ${appConfig.graphiteHost}:${appConfig.graphitePort}"
        )

        Using.Manager { use =>
          val socket = use(new Socket())

          socket.setSoTimeout(appConfig.graphiteReadTimeoutMs)
          socket.connect(
            new InetSocketAddress(
              appConfig.graphiteHost,
              appConfig.graphitePort
            ),
            appConfig.graphiteConnectTimeoutMs
          )

          val writer = use(
            new BufferedWriter(
              new OutputStreamWriter(
                socket.getOutputStream,
                StandardCharsets.UTF_8
              )
            )
          )

          metrics.foreach { line =>
            writer.write(line)
            writer.newLine()
          }

          writer.flush()
        } match {
          case Success(_) =>
            logger.info(
              s"Successfully sent ${metrics.size} metrics to Graphite"
            )

          case Failure(e) =>
            logger.error(
              s"Failed to send ${metrics.size} metrics to Graphite at ${appConfig.graphiteHost}:${appConfig.graphitePort}",
              e
            )
            throw e
        }
      } else {
        logger.info("No Nest metrics available to send to Graphite")
      }
    }

  private def metric(
      name: String,
      value: Any,
      timestamp: Long
  ): String =
    s"$metricPrefix.$name $value $timestamp"

  private def binaryMetric(
      name: String,
      value: String,
      onValue: String,
      offValue: String,
      timestamp: Long
  ): String =
    value match {
      case `onValue` =>
        metric(name, 1, timestamp)

      case `offValue` =>
        metric(name, 0, timestamp)

      case other =>
        val ex = new RuntimeException(
          s"Invalid value for metric '$name': '$other' " +
            s"(expected '$onValue' or '$offValue')"
        )
        logger.error(ex.getMessage)
        throw ex
    }
}
