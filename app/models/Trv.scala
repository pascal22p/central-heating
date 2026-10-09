package models

import play.api.libs.json.*

enum RunningState derives CanEqual {
  case Idle, Heat
}

enum SystemMode {
  case Heat, Auto
}

enum TemperatureSensorSelection {
  case Internal
}

enum ChildLock {
  case Locked, Unlocked
}

final case class Trv(
    name: String,
    battery: Int,
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

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def enumFormat[E](values: Array[E]): Format[E] =
    Format(
      Reads {
        case JsString(s) =>
          values
            .find(_.toString == s)
            .fold[JsResult[E]](JsError(s"Unknown value: $s"))(JsSuccess(_))
        case _ => JsError("error.expected.jsstring")
      },
      Writes(e => JsString(e.toString))
    )

  val sqlFormat: Format[Trv] = {
    given Format[RunningState]               = enumFormat(RunningState.values)
    given Format[SystemMode]                 = enumFormat(SystemMode.values)
    given Format[TemperatureSensorSelection] = enumFormat(TemperatureSensorSelection.values)
    given Format[ChildLock]                  = enumFormat(ChildLock.values)
    Json.format[Trv]
  }

  given Reads[Trv] =
    Reads { json =>
      for
        battery <-
          (json \ "battery").validate[Int]

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

      yield Trv(
        name = "",
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
          case value  =>
            throw new IllegalArgumentException(
              s"Unknown running_state: $value"
            )
        },
        systemMode = systemModeValue match {
          case "heat" => SystemMode.Heat
          case "auto" => SystemMode.Auto
          case value  =>
            throw new IllegalArgumentException(
              s"Unknown system_mode: $value"
            )
        },
        temperatureSensorSelection = temperatureSensorSelectionValue match {
          case "internal" =>
            TemperatureSensorSelection.Internal
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
