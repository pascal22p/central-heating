package services

import play.api.libs.json.{ Json, Writes }
import scala.concurrent.{ ExecutionContext, Future }

import uk.gov.hmrc.http.HeaderCarrier

import org.mockito.ArgumentMatchers.{ any, argThat }
import org.mockito.Mockito.*

import cats.data.OptionT
import config.AppConfig
import models.mqtt.{ ChildLock, RunningState, SystemMode, TemperatureSensor, TemperatureSensorSelection, Trv }
import queries.DevicesQueries
import testUtils.BaseSpec

class MqttMessageHandlerServiceSpec extends BaseSpec {

  private implicit val ec: ExecutionContext =
    ExecutionContext.global

  private lazy val mockDevicesQueries                 = mock[DevicesQueries]
  private lazy val mockHeatingControlService          = mock[HeatingControlService]
  private lazy val mockAppConfig                      = mock[AppConfig]
  private lazy val mockTemperatureSensorUpdateService = mock[TemperatureSensorUpdateService]

  private val trvTopic         = "trv/living-room"
  private val temperatureTopic = "temperature/living-room"
  private val unknownTopic     = "unknown/device"

  private implicit val hc: HeaderCarrier = HeaderCarrier()

  private lazy val service = new MqttMessageHandlerService(
    mockDevicesQueries,
    mockHeatingControlService,
    mockTemperatureSensorUpdateService,
    mockAppConfig
  )

  private val temperatureSensorWrites: Writes[TemperatureSensor] = Writes { sensor =>
    Json.obj(
      "temperature" -> sensor.temperature,
      "humidity"    -> sensor.humidity,
      "battery"     -> sensor.battery
    )
  }

  private val trvWrites: Writes[Trv] = Writes { trv =>
    Json.obj(
      "battery"                       -> trv.battery,
      "external_temperature_input"    -> trv.externalTemperatureInput,
      "heat_percentage_hour"          -> trv.heatPercentageHour,
      "linkquality"                   -> trv.linkQuality,
      "local_temperature"             -> trv.localTemperature,
      "local_temperature_calibration" -> trv.localTemperatureCalibration,
      "occupied_heating_setpoint"     -> trv.occupiedHeatingSetpoint,
      "running_state"                 -> (trv.runningState match {
        case RunningState.Idle => "idle"
        case RunningState.Heat => "heat"
        case RunningState.Off  => "off"
      }),
      "system_mode" -> (trv.systemMode match {
        case SystemMode.Heat => "heat"
        case SystemMode.Auto => "auto"
      }),
      "temperature_sensor_select" -> (trv.temperatureSensorSelection match {
        case TemperatureSensorSelection.Internal      => "internal"
        case TemperatureSensorSelection.Remote        => "remote_temperature"
        case TemperatureSensorSelection.RemoteOffline => "remote_source_offline"
      }),
      "open_window_detected" -> trv.openWindowDetected,
      "child_lock"           -> (trv.childLock match {
        case ChildLock.Locked   => "LOCK"
        case ChildLock.Unlocked => "UNLOCK"
      }),
      "smart_temperature_control" -> trv.smartTemperatureControl
    )
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockDevicesQueries, mockHeatingControlService, mockAppConfig, mockTemperatureSensorUpdateService)

    when(mockAppConfig.trvDevices)
      .thenReturn(Set(trvTopic))

    when(mockAppConfig.temperatureDevices)
      .thenReturn(Set(temperatureTopic))

    when(mockTemperatureSensorUpdateService.onTemperatureUpdate(any())(using any())).thenReturn(
      OptionT.none[Future, Unit]
    )

    ()
  }

  "MqttMessageHandlerService.handle" should {

    "save a valid TRV with its name set to the topic and trigger an update" in {
      val trv     = fakeTrv(name = "Payload name")
      val payload = Json.stringify(Json.toJson(trv)(using trvWrites))

      when(mockDevicesQueries.saveTrv(any[Trv]))
        .thenReturn(Future.unit)

      when(mockHeatingControlService.onTrvUpdate(any[Trv])(using any()))
        .thenReturn(Future.unit)

      service.handle(trvTopic, payload).futureValue

      verify(mockDevicesQueries).saveTrv(
        argThat[Trv](_.name == trvTopic)
      )

      verify(mockHeatingControlService).onTrvUpdate(
        argThat[Trv](_.name == trvTopic)
      )(using any())
    }

    "save a valid temperature sensor with its name set to the topic and trigger an update" in {
      val sensor  = fakeTemperatureSensor(name = "Payload name")
      val payload = Json.stringify(Json.toJson(sensor)(using temperatureSensorWrites))

      when(
        mockDevicesQueries.saveTemperatureSensor(
          any[TemperatureSensor]
        )
      ).thenReturn(Future.successful(()))

      when(
        mockHeatingControlService.onTemperatureSensorUpdate(
          any[TemperatureSensor]
        )(using any())
      ).thenReturn(Future.successful(()))

      service.handle(temperatureTopic, payload).futureValue

      verify(mockDevicesQueries).saveTemperatureSensor(
        argThat[TemperatureSensor](_.name == temperatureTopic)
      )

      verify(mockHeatingControlService).onTemperatureSensorUpdate(
        argThat[TemperatureSensor](_.name == temperatureTopic)
      )(using any())
    }

    "ignore malformed JSON" in {
      service.handle(trvTopic, "{invalid-json").futureValue

      verifyNoInteractions(mockDevicesQueries)
      verifyNoInteractions(mockHeatingControlService)
    }

    "ignore a valid JSON payload that does not match the TRV model" in {
      service.handle(trvTopic, """{"unexpected": true}""").futureValue

      verifyNoInteractions(mockDevicesQueries)
      verifyNoInteractions(mockHeatingControlService)
    }

    "ignore a valid JSON payload that does not match the temperature sensor model" in {
      service.handle(temperatureTopic, """{"unexpected": true}""").futureValue

      verifyNoInteractions(mockDevicesQueries)
      verifyNoInteractions(mockHeatingControlService)
    }

    "ignore messages received on an unexpected topic" in {
      service.handle(unknownTopic, """{"value": 1}""").futureValue

      verifyNoInteractions(mockDevicesQueries)
      verifyNoInteractions(mockHeatingControlService)
    }
  }
}
