package funnel
package integration

import concurrent.duration._
import chemist.{Flask,FlaskID,Location,LocationIntent,LocationTemplate,Target,NetworkScheme}
import flask.Options
import java.net.URI
import zeromq.TCP

object IntegrationFixtures {
  val defaultTemplates =
    List(LocationTemplate("http://@host:@port/stream/previous"))

  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = defaultTemplates)

  val telemetryLocalhost: Location =
    Location(
      host = "127.0.0.1",
      port = 7390,
      datacenter = "local",
      protocol = NetworkScheme.Zmtp(TCP),
      intent = LocationIntent.Supervision,
      templates = defaultTemplates)

  val flask1 = Flask(
    FlaskID("flask1"),
    localhost,
    telemetryLocalhost)

  val flask1Options = Options(
    name = Some(flask1.id.value),
    cluster = None,
    retriesDuration = 30.seconds,
    maxRetries = 6,
    funnelPort = flask1.location.port,
    telemetryPort = flask1.telemetry.port)

  val flask2 = Flask(
    FlaskID("flask2"),
    localhost.copy(port = 5776),
    telemetryLocalhost.copy(port = 7391))

  val flask2Options = Options(
    name = Some(flask2.id.value),
    cluster = None,
    retriesDuration = 30.seconds,
    maxRetries = 6,
    funnelPort = flask2.location.port,
    telemetryPort = flask2.telemetry.port)

  val flaskOptionsWithES = flask1Options.copy(
    elastic = Some(elastic.ElasticCfg(
      url = "http://localhost:9200",
      indexName = "funnel",
      typeName = "metric",
      dateFormat = "yyyy.MM.dd",
      templateName = "flask",
      templateLocation = None,
      groups = List("previous/jvm", "previous/system", "previous")
    )))

  lazy val flasks =
    flask1 ::
    flask2 :: Nil

  lazy val targets =
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
