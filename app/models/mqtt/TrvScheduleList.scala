package models.mqtt

import play.api.libs.json.{ Format, Json, Reads, Writes }

final case class TrvScheduleList(
    monday: String,
    tuesday: String,
    wednesday: String,
    thursday: String,
    friday: String,
    saturday: String,
    sunday: String
) derives CanEqual

object TrvScheduleList {
  val sqlFormat: Format[TrvScheduleList] = Json.format[TrvScheduleList]

  val mqttWrites: Writes[TrvScheduleList] =
    Writes { s =>
      Json.obj(
        "weekly_schedule_monday"    -> s.monday,
        "weekly_schedule_tuesday"   -> s.tuesday,
        "weekly_schedule_wednesday" -> s.wednesday,
        "weekly_schedule_thursday"  -> s.thursday,
        "weekly_schedule_friday"    -> s.friday,
        "weekly_schedule_saturday"  -> s.saturday,
        "weekly_schedule_sunday"    -> s.sunday
      )
    }

  val mqttReads: Reads[TrvScheduleList] =
    Reads { json =>
      for {
        monday    <- (json \ "weekly_schedule_monday").validate[String]
        tuesday   <- (json \ "weekly_schedule_tuesday").validate[String]
        wednesday <- (json \ "weekly_schedule_wednesday").validate[String]
        thursday  <- (json \ "weekly_schedule_thursday").validate[String]
        friday    <- (json \ "weekly_schedule_friday").validate[String]
        saturday  <- (json \ "weekly_schedule_saturday").validate[String]
        sunday    <- (json \ "weekly_schedule_sunday").validate[String]
      } yield TrvScheduleList(
        monday = monday,
        tuesday = tuesday,
        wednesday = wednesday,
        thursday = thursday,
        friday = friday,
        saturday = saturday,
        sunday = sunday
      )
    }
}
