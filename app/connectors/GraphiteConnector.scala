package connectors

import java.io.{ BufferedWriter, OutputStreamWriter }
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }

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

      val metrics = Seq(
        device.traits.temperatureCelsius.map(value => metric("temperature_celsius", value, timestamp)),
        device.traits.humidityPercent.map(value => metric("humidity_percent", value, timestamp)),
        device.traits.heatSetpointCelsius.map(value => metric("heat_setpoint_celsius", value, timestamp)),
        device.traits.coolSetpointCelsius.map(value => metric("cool_setpoint_celsius", value, timestamp)),
        device.traits.ecoHeatCelsius.map(value => metric("eco_heat_celsius", value, timestamp)),
        device.traits.ecoCoolCelsius.map(value => metric("eco_cool_celsius", value, timestamp)),
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
      ).flatten

      if (metrics.nonEmpty) {
        logger.info(
          s"Sending ${metrics.size} metrics to Graphite at ${appConfig.graphiteHost}:${appConfig.graphitePort}"
        )

        val socket = new Socket(
          appConfig.graphiteHost,
          appConfig.graphitePort
        )

        try {
          val writer = new BufferedWriter(
            new OutputStreamWriter(
              socket.getOutputStream,
              StandardCharsets.UTF_8
            )
          )

          metrics.foreach { line =>
            writer.write(line)
            writer.newLine()
          }

          writer.flush()

          logger.info(
            s"Successfully sent ${metrics.size} metrics to Graphite"
          )
        } finally {
          socket.close()
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
      value: Option[String],
      onValue: String,
      offValue: String,
      timestamp: Long
  ): Option[String] =
    value.flatMap {
      case `onValue` =>
        Some(metric(name, 1, timestamp))

      case `offValue` =>
        Some(metric(name, 0, timestamp))

      case _ =>
        None
    }
}
