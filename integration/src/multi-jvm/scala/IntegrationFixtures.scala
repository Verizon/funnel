package funnel
package integration

import flask.Options
import chemist.{Flask,FlaskID,Location}

object IntegrationFixtures {
  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = "http")

  val telemetryLocalhost: Location =
    Location(
      host = "127.0.0.1",
      port = 7390,
      datacenter = "local",
      protocol = "tcp")

  val flask1 = Flask(
    FlaskID("flask1"),
    localhost,
    telemetryLocalhost)

  val flask1Options = Options(
    name = Some(flask1.id.value),
    cluster = Some("cluster1"),
    funnelPort = flask1.location.port,
    telemetryPort = flask1.telemetry.port)
}
