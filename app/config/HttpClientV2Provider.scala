package config

import javax.inject.{ Inject, Provider, Singleton }
import play.api.libs.ws.WSClient
import play.api.Configuration

import uk.gov.hmrc.http.client.{ HttpClientV2, HttpClientV2Impl }

import org.apache.pekko.actor.ActorSystem

@Singleton
class HttpClientV2Provider @Inject() (
    config: Configuration,
    wsClient: WSClient,
    actorSystem: ActorSystem
) extends Provider[HttpClientV2] {

  private lazy val instance = new HttpClientV2Impl(
    wsClient,
    actorSystem,
    config = config,
    hooks = Seq.empty
  )

  override def get(): HttpClientV2 =
    instance
}
