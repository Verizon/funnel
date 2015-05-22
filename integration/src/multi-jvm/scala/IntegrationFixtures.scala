package funnel
package integration

import flask.Options

object IntegrationFixtures {
  val flask1Options = Options(
    name = Some("flask1"),
    cluster = Some("cluster1"),
    funnelPort = 6775,
    telemetryPort = 7390)
}


