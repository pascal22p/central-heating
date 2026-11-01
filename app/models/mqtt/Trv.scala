package models.mqtt

import play.api.libs.json.*

import models.*
import utils.EnumFormats

final case class Trv(
    name: String,
    battery: Option[Int],
    externalTemperatureInput: Double,
    heatPercentageHour: Double,
    linkQuality: Int,
    localTemperature: Double,
    localTemperatureCalibration: Double,
    occupiedHeatingSetpoint: Double,
    runningState: RunningState,
    systemMode: SystemMode,
    temperatureSensorSelection: TemperatureSensorSelection,
    openWindowDetected: Boolean,
    childLock: ChildLock,
    smartTemperatureControl: String
) derives CanEqual

object Trv {

  val sqlFormat: Format[Trv] = {
    given Format[RunningState]               = EnumFormats.enumFormat(RunningState.values)
    given Format[SystemMode]                 = EnumFormats.enumFormat(SystemMode.values)
    given Format[TemperatureSensorSelection] = EnumFormats.enumFormat(TemperatureSensorSelection.values)
    given Format[ChildLock]                  = EnumFormats.enumFormat(ChildLock.values)
    Json.format[Trv]
  }

  given Reads[Trv] =
    Reads { json =>
      for {
        name <-
          (json \ "name").validate[String]

        battery <-
          (json \ "battery").validateOpt[Int]

        externalTemperatureInput <-
          (json \ "external_temperature_input")
            .validate[Double]

        heatPercentageHour <-
          (json \ "heat_percentage_hour")
            .validate[Double]

        linkQuality <-
          (json \ "linkquality").validate[Int]

        localTemperature <-
          (json \ "local_temperature")
            .validate[Double]

        localTemperatureCalibration <-
          (json \ "local_temperature_calibration")
            .validate[Double]

        occupiedHeatingSetpoint <-
          (json \ "occupied_heating_setpoint")
            .validate[Double]

        runningStateValue <-
          (json \ "running_state").validate[String]

        systemModeValue <-
          (json \ "system_mode").validate[String]

        temperatureSensorSelectionValue <-
          (json \ "temperature_sensor_select")
            .validate[String]

        openWindowDetected <-
          (json \ "open_window_detected")
            .validate[Boolean]

        childLockValue <-
          (json \ "child_lock").validate[String]

        smartTemperatureControl <-
          (json \ "smart_temperature_control")
            .validate[String]
      } yield Trv(
        name = name,
        battery = battery,
        externalTemperatureInput = externalTemperatureInput,
        heatPercentageHour = heatPercentageHour,
        linkQuality = linkQuality,
        localTemperature = localTemperature,
        localTemperatureCalibration = localTemperatureCalibration,
        occupiedHeatingSetpoint = occupiedHeatingSetpoint,
        runningState = runningStateValue match {
          case "idle" => RunningState.Idle
          case "heat" => RunningState.Heat
          case "off"  => RunningState.Off
          case value  =>
            throw new IllegalArgumentException(
              s"Unknown running_state: $value"
            )
        },
        systemMode = systemModeValue match {
          case "heat" => SystemMode.Heat
          case "auto" => SystemMode.Auto
          case "off"  => SystemMode.Off
          case value  =>
            throw new IllegalArgumentException(
              s"Unknown system_mode: $value"
            )
        },
        temperatureSensorSelection = temperatureSensorSelectionValue match {
          case "internal" =>
            TemperatureSensorSelection.Internal
          case "local_temperature" =>
            TemperatureSensorSelection.Internal
          case "remote_temperature" =>
            TemperatureSensorSelection.Remote
          case "remote_source_offline" =>
            TemperatureSensorSelection.RemoteOffline
          case value =>
            throw new IllegalArgumentException(
              s"Unknown temperature_sensor_select: $value"
            )
        },
        openWindowDetected = openWindowDetected,
        childLock = childLockValue match {
          case "LOCK"   => ChildLock.Locked
          case "UNLOCK" => ChildLock.Unlocked
          case value    =>
            throw new IllegalArgumentException(
              s"Unknown child_lock: $value"
            )
        },
        smartTemperatureControl = smartTemperatureControl
      )
    }
}
