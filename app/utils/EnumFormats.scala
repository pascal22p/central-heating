package utils

import java.util.Locale
import play.api.libs.json.{ Format, JsError, JsResult, JsString, JsSuccess, Reads, Writes }

object EnumFormats {
  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def enumFormat[E](values: Array[E]): Format[E] =
    Format(
      Reads {
        case JsString(s) =>
          values
            .find(_.toString.toLowerCase(Locale.ENGLISH) == s.toLowerCase(Locale.ENGLISH))
            .fold[JsResult[E]](JsError(s"Unknown value: $s"))(JsSuccess(_))
        case _ => JsError("error.expected.jsstring")
      },
      Writes(e => JsString(e.toString))
    )
}
