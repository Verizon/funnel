package funnel
package chemist

object Fixtures {


  val flask01 = Flask(FlaskID("flask01"),
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty))

  val flask02 = Flask(FlaskID("flask02"),
    Location(
      host = "127.0.0.1",
      port = 6548,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty))

  val flask03 = Flask(FlaskID("flask03"),
    Location(
      host = "127.0.0.1",
      port = 4532,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty))
}
