package queries

import javax.inject.{ Inject, Singleton }
import play.api.db.Database
import play.api.libs.json.Json
import scala.concurrent.Future

import anorm.*
import models.mqtt.{ DeviceType, TemperatureSensor, Trv }
import models.DatabaseExecutionContext

@Singleton
final class DevicesQueries @Inject() (
    db: Database,
    databaseExecutionContext: DatabaseExecutionContext
) {

  def saveTrv(trv: Trv): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO devices (
          |    name,
          |    deviceType,
          |    data,
          |    expires_at
          |)
          |VALUES (
          |    {name},
          |    {deviceType},
          |    {data},
          |    CURRENT_TIMESTAMP + INTERVAL 1 HOUR
          |)
          |ON DUPLICATE KEY UPDATE
          |    data = VALUES(data),
          |    expires_at = CURRENT_TIMESTAMP + INTERVAL 1 HOUR""".stripMargin
      )
        .on(
          "name"       -> trv.name,
          "deviceType" -> s"${DeviceType.TRV}",
          "data"       -> Json.stringify(Json.toJson(trv)(using Trv.sqlFormat))
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def getTrvs: Future[Seq[Trv]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM devices
          |WHERE deviceType = {deviceType} AND expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("deviceType" -> s"${DeviceType.TRV}")
        .as(
          SqlParser
            .str("data")
            .map {
              case data =>
                Json
                  .parse(data)
                  .as[Trv](using Trv.sqlFormat)
            }
            .*
        )
    }
  }(using databaseExecutionContext)

  def getTrv(deviceName: String): Future[Option[Trv]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM devices
          |WHERE name = {name} AND deviceType = {deviceType} AND expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("name" -> deviceName, "deviceType" -> s"${DeviceType.TRV}")
        .as(
          SqlParser
            .str("data")
            .map(Json.parse(_).as[Trv](using Trv.sqlFormat))
            .singleOpt
        )
    }
  }(using databaseExecutionContext)

  def saveTemperatureSensor(sensor: TemperatureSensor): Future[Unit] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """INSERT INTO devices (
          |    name,
          |    deviceType,
          |    data,
          |    expires_at
          |)
          |VALUES (
          |    {name},
          |    {deviceType},
          |    {data},
          |    CURRENT_TIMESTAMP + INTERVAL 1 HOUR
          |)
          |ON DUPLICATE KEY UPDATE
          |    data = VALUES(data),
          |    expires_at = CURRENT_TIMESTAMP + INTERVAL 1 HOUR""".stripMargin
      )
        .on(
          "name"       -> sensor.name,
          "deviceType" -> s"${DeviceType.TEMPERATURE_SENSOR}",
          "data"       -> Json.stringify(Json.toJson(sensor)(using TemperatureSensor.sqlFormat))
        )
        .execute()
    }
    ()
  }(using databaseExecutionContext)

  def getTemperatureSensors: Future[Seq[TemperatureSensor]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM devices
          |WHERE deviceType = {deviceType} AND expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("deviceType" -> s"${DeviceType.TEMPERATURE_SENSOR}")
        .as(
          SqlParser
            .str("data")
            .map(Json.parse(_).as[TemperatureSensor](using TemperatureSensor.sqlFormat))
            .*
        )
    }
  }(using databaseExecutionContext)

  def getTemperatureSensor(deviceName: String): Future[Option[TemperatureSensor]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT data
          |FROM devices
          |WHERE name = {name} AND deviceType = {deviceType} AND expires_at > CURRENT_TIMESTAMP""".stripMargin
      )
        .on("name" -> deviceName, "deviceType" -> s"${DeviceType.TEMPERATURE_SENSOR}")
        .as(
          SqlParser
            .str("data")
            .map(Json.parse(_).as[TemperatureSensor](using TemperatureSensor.sqlFormat))
            .singleOpt
        )
    }
  }(using databaseExecutionContext)

}
