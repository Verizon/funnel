package funnel
package integration

import flask.Options
import chemist.{Flask,FlaskID,Location}

object IntegrationFixtures {
  val flask1 = Flask(
    FlaskID("flask1"),
    Location.localhost.copy(port = 6775),
    Location.telemetryLocalhost)

  val flask1Options = Options(
    name = Some(flask1.id.value),
    cluster = Some("cluster1"),
    funnelPort = flask1.location.port,
    telemetryPort = flask1.telemetry.port)
}
