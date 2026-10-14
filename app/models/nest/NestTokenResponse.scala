package models.nest

import play.api.libs.json._

final case class NestTokenResponse(
    access_token: String,
    expires_in: Long
)

object NestTokenResponse {
  implicit val reads: Reads[NestTokenResponse] =
    Json.reads[NestTokenResponse]
}
