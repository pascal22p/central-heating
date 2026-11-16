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

  private def save(name: String, deviceType: DeviceType, data: String): Future[Int] = Future {
    db.withTransaction { implicit conn =>
      // register the topic if this is the first message from the device
      SQL("INSERT IGNORE INTO topics (name) VALUES ({name})")
        .on("name" -> name)
        .executeUpdate()

      SQL(
        """INSERT INTO devices (topic, deviceType, data, expires_at)
          |SELECT id, {deviceType}, {data}, CURRENT_TIMESTAMP + INTERVAL 1 HOUR
          |FROM topics
          |WHERE name = {name}
          |ON DUPLICATE KEY UPDATE
          |    deviceType = VALUES(deviceType),
          |    data = VALUES(data),
          |    expires_at = VALUES(expires_at)""".stripMargin
      )
        .on(
          "name"       -> name,
          "deviceType" -> s"$deviceType",
          "data"       -> data
        )
        .executeUpdate()
    }
  }(using databaseExecutionContext)

  def saveTrv(trv: Trv): Future[Int] =
    save(trv.name, DeviceType.TRV, Json.stringify(Json.toJson(trv)(using Trv.sqlFormat)))

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
            .map(Json.parse(_).as[Trv](using Trv.sqlFormat))
            .*
        )
    }
  }(using databaseExecutionContext)

  def getTrv(deviceName: String): Future[Option[Trv]] = Future {
    db.withConnection { implicit conn =>
      SQL(
        """SELECT d.data
          |FROM devices d
          |JOIN topics t ON t.id = d.topic
          |WHERE t.name = {name}
          |  AND d.deviceType = {deviceType}
          |  AND d.expires_at > CURRENT_TIMESTAMP""".stripMargin
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

  def saveTemperatureSensor(sensor: TemperatureSensor): Future[Int] =
    save(
      sensor.name,
      DeviceType.TEMPERATURE_SENSOR,
      Json.stringify(Json.toJson(sensor)(using TemperatureSensor.sqlFormat))
    )

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
        """SELECT d.data
          |FROM devices d
          |JOIN topics t ON t.id = d.topic
          |WHERE t.name = {name}
          |  AND d.deviceType = {deviceType}
          |  AND d.expires_at > CURRENT_TIMESTAMP""".stripMargin
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
