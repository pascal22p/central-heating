package connectors

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ Executors, TimeUnit }
import play.api.test.Helpers.{ await, defaultAwaitTimeout }
import scala.concurrent.ExecutionContext

import org.mockito.Mockito.when

import models.{ NestDevice, NestTraits }
import testUtils.BaseSpec

class GraphiteConnectorSpec extends BaseSpec {

  implicit lazy val ec: ExecutionContext =
    scala.concurrent.ExecutionContext.global

  "GraphiteConnector" should {

    "send the Nest metrics to Graphite" in {
      val server = new ServerSocket(0)
      val port   = server.getLocalPort

      val executor = Executors.newSingleThreadExecutor()

      try {
        val received = executor.submit[String](() => {
          val socket = server.accept()

          try {
            val input     = socket.getInputStream
            val buffer    = new Array[Byte](4096)
            val bytesRead = input.read(buffer)

            new String(
              buffer,
              0,
              bytesRead,
              StandardCharsets.UTF_8
            )
          } finally {
            socket.close()
          }
        })

        val appConfig = mock[config.AppConfig]

        when(appConfig.graphiteHost).thenReturn("localhost")
        when(appConfig.graphitePort).thenReturn(port)
        when(appConfig.graphitePrefix).thenReturn("heating.nest")

        val sut =
          new GraphiteConnector(appConfig)

        val device = NestDevice(
          name = "Nest Thermostat",
          deviceType = "sdm.devices.types.THERMOSTAT",
          traits = NestTraits(
            connectivity = Some("ONLINE"),
            temperatureCelsius = Some(19.5),
            humidityPercent = Some(54.0),
            thermostatMode = Some("HEAT"),
            heatSetpointCelsius = Some(21.0),
            coolSetpointCelsius = Some(25.0),
            hvacStatus = Some("OFF"),
            ecoMode = Some("MANUAL_ECO"),
            ecoHeatCelsius = Some(16.0),
            ecoCoolCelsius = Some(24.0),
            fanTimerMode = Some("OFF"),
            fanTimerTimeout = None
          )
        )

        await(sut.send(device))

        val payload =
          received.get(5, TimeUnit.SECONDS)

        val lines =
          payload.trim.linesIterator.toSeq

        lines.size mustBe 9

        lines.exists(_.startsWith("heating.nest.temperature_celsius 19.5 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.humidity_percent 54.0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.heat_setpoint_celsius 21.0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.cool_setpoint_celsius 25.0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.eco_heat_celsius 16.0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.eco_cool_celsius 24.0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.thermostat_mode 1 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.hvac_status 0 ")) mustBe true
        lines.exists(_.startsWith("heating.nest.eco_mode 1 ")) mustBe true

        lines.foreach { line =>
          (line.split(" ") must have).length(3)
        }
      } finally {
        server.close()
        executor.shutdownNow(): Unit
      }
    }

    "send zero for the off value of each binary metric" in {
      val server = new ServerSocket(0)
      val port   = server.getLocalPort

      val executor = Executors.newSingleThreadExecutor()

      try {
        val received = executor.submit[String](() => {
          val socket = server.accept()

          try {
            val input     = socket.getInputStream
            val buffer    = new Array[Byte](4096)
            val bytesRead = input.read(buffer)

            new String(
              buffer,
              0,
              bytesRead,
              StandardCharsets.UTF_8
            )
          } finally {
            socket.close()
          }
        })

        val appConfig = mock[config.AppConfig]

        when(appConfig.graphiteHost).thenReturn("localhost")
        when(appConfig.graphitePort).thenReturn(port)
        when(appConfig.graphitePrefix).thenReturn("heating.nest")

        val sut =
          new GraphiteConnector(appConfig)

        val device = NestDevice(
          name = "Nest Thermostat",
          deviceType = "sdm.devices.types.THERMOSTAT",
          traits = NestTraits(
            connectivity = None,
            temperatureCelsius = None,
            humidityPercent = None,
            thermostatMode = Some("OFF"),
            heatSetpointCelsius = None,
            coolSetpointCelsius = None,
            hvacStatus = Some("OFF"),
            ecoMode = Some("OFF"),
            ecoHeatCelsius = None,
            ecoCoolCelsius = None,
            fanTimerMode = None,
            fanTimerTimeout = None
          )
        )

        await(sut.send(device))

        val payload =
          received.get(5, TimeUnit.SECONDS)

        val lines =
          payload.trim.linesIterator.toSeq

        lines must contain("heating.nest.thermostat_mode 0 " + lines.head.split(" ").last)
        lines must contain("heating.nest.hvac_status 0 " + lines.head.split(" ").last)
        lines must contain("heating.nest.eco_mode 0 " + lines.head.split(" ").last)
      } finally {
        server.close()
        executor.shutdownNow(): Unit
      }
    }

    "not send metrics for unavailable values" in {
      val server = new ServerSocket(0)
      val port   = server.getLocalPort

      val executor = Executors.newSingleThreadExecutor()

      try {
        val received = executor.submit[String](() => {
          val socket = server.accept()

          try {
            val input     = socket.getInputStream
            val buffer    = new Array[Byte](4096)
            val bytesRead = input.read(buffer)

            new String(
              buffer,
              0,
              bytesRead,
              StandardCharsets.UTF_8
            )
          } finally {
            socket.close()
          }
        })

        val appConfig = mock[config.AppConfig]

        when(appConfig.graphiteHost).thenReturn("localhost")
        when(appConfig.graphitePort).thenReturn(port)
        when(appConfig.graphitePrefix).thenReturn("heating.nest")

        val sut =
          new GraphiteConnector(appConfig)

        val device = NestDevice(
          name = "Nest Thermostat",
          deviceType = "sdm.devices.types.THERMOSTAT",
          traits = NestTraits(
            connectivity = None,
            temperatureCelsius = Some(19.5),
            humidityPercent = None,
            thermostatMode = None,
            heatSetpointCelsius = None,
            coolSetpointCelsius = None,
            hvacStatus = None,
            ecoMode = None,
            ecoHeatCelsius = None,
            ecoCoolCelsius = None,
            fanTimerMode = None,
            fanTimerTimeout = None
          )
        )

        await(sut.send(device))

        val payload =
          received.get(5, TimeUnit.SECONDS)

        val lines =
          payload.trim.linesIterator.toSeq

        lines must have size 1
        lines.head.startsWith(
          "heating.nest.temperature_celsius 19.5 "
        ) mustBe true
      } finally {
        server.close()
        executor.shutdownNow(): Unit
      }
    }
  }
}
