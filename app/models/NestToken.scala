package models

final case class NestToken(
    accessToken: String,
    expiresAt: Long
) {
  private val expirySafetyBufferMillis = 30 * 1000

  def isExpired: Boolean =
    System.currentTimeMillis() >= expiresAt - expirySafetyBufferMillis
}
