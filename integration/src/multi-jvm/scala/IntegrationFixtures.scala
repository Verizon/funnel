package funnel
package integration

import chemist.{Flask,FlaskID,Location,Target}
import flask.Options
import java.net.URI

object IntegrationFixtures {
  val flask1 = Flask(
    FlaskID("flask1"),
    Location.localhost.copy(port = 5775),
    Location.telemetryLocalhost)

  val flask1Options = Options(
    name = Some(flask1.id.value),
    cluster = Some("cluster1"),
    funnelPort = flask1.location.port,
    telemetryPort = flask1.telemetry.port)

  val targets =
    target01 ::
    target02 ::
    target03 :: Nil

  val target01 = Target(
    cluster = "target01",
    uri = new URI("http://localhost:4001/stream/now"),
    isPrivateNetwork = true
  )
  val target02 = Target(
    cluster = "target02",
    uri = new URI("http://localhost:4002/stream/now"),
    isPrivateNetwork = true
  )
  val target03 = Target(
    cluster = "target03",
    uri = new URI("http://localhost:4003/stream/now"),
    isPrivateNetwork = true
  )

}
