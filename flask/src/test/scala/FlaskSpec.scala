package funnel
package flask

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scalaz.stream.Process
import scalaz.stream.async.signal
import com.amazonaws.auth.BasicAWSCredentials
import argonaut._, Argonaut._
import journal.Logger
import funnel.elastic._
import funnel.http.JSON._
import funnel.zeromq._
import sockets._

class FlaskSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  import java.io.File
  import knobs.{ ClassPathResource, Config, FileResource, Required }
  import dispatch._

  val config: Task[Config] = for {
    a <- knobs.loadImmutable(List(Required(
      FileResource(new File("/usr/share/oncue/etc/flask.cfg")) or
        ClassPathResource("oncue/flask.cfg"))))
    b <- knobs.aws.config
  } yield a ++ b

  val (options, cfg) = config.flatMap { cfg =>
    val name             = cfg.lookup[String]("flask.name")
    val cluster           = cfg.lookup[String]("flask.cluster")
    val elasticURL       = cfg.lookup[String]("flask.elastic-search.url")
    val elasticIx        = cfg.lookup[String]("flask.elastic-search.index-name")
    val elasticTy        = cfg.lookup[String]("flask.elastic-search.type-name")
    val elasticDf        =
      cfg.lookup[String]("flask.elastic-search.partition-date-format").getOrElse("yyyy.MM.dd")
    val elasticTimeout   = cfg.lookup[Int]("flask.elastic-search.connection-timeout-in-ms").getOrElse(5000)
    val esGroups         = cfg.lookup[List[String]]("flask.elastic-search.groups")
    val riemannHost      = cfg.lookup[String]("flask.riemann.host")
    val riemannPort      = cfg.lookup[Int]("flask.riemann.port")
    val ttl              = cfg.lookup[Int]("flask.riemann.ttl-in-minutes").map(_.minutes)
    val riemann          = (riemannHost |@| riemannPort |@| ttl)(RiemannCfg)
    val elastic          = (elasticURL |@| elasticIx |@| elasticTy |@| esGroups)(
      ElasticCfg(_, _, _, elasticDf, "foo", None, _))
    val port             = cfg.lookup[Int]("flask.network.port").getOrElse(5775)
    Task((Options(name, cluster, elastic, riemann, port), cfg))
  }.run

  val log = Logger[this.type]

  val flaskUrl = url(s"http://localhost:${options.funnelPort}/mirror").setContentType("application/json", "UTF-8")

  val payload = s"""
  [
    {
      "cluster": "datapoints-1.0-us-east",
      "urls": [
        "${Settings.tcp}"
      ]
    }
  ]
  """

  val ready = signal[Boolean]
  ready.set(false)

  implicit val B = scalaz.std.anyVal.booleanInstance.conjunction
  implicit val s = scalaz.stream.DefaultScheduler

  val E = Endpoint.unsafeApply(publish &&& bind, Settings.tcp)

  implicit val batransport: Transportable[Array[Byte]] = Transportable { ba =>
    Transported(Schemes.unknown, Versions.v1, None, None, ba)
  }

  val seq: Seq[Array[Byte]] = (0 until 5000) map { i =>
    Datapoint(Key[Double]("now/life", Units.Count: Units, 0.0,
                          "description", Map("url" -> "http://localhost")),
              42.0).asJson.spaces2.getBytes
  }
  val k: Seq[Boolean] = seq.map(_ => true) ++ Seq(false)

  val proc: Process[Task, Array[Byte]] = Process.emitAll(seq) fby Process.eval_(ready.set(true))
  val alive: Process[Task, Boolean] = Process.emitAll(k)

  val app = new Flask(options, new Instruments(1.minute))

  override def beforeAll() {
    app.run(Array())
    Http(flaskUrl << payload OK as.String)(concurrent.ExecutionContext.Implicits.global)
    ()
  }

  override def afterAll() {
    app.S.stop()
  }

  if (Ø.isEnabled) {
    "mirrorDatapoints" should "be 5000" in {
      app.I.monitoring.get(app.mirrorDatapoints.keys.now).discrete.sleepUntil(ready.discrete.once).once.runLast.map(_.get).runAsync { d =>
        d.fold (
          t =>
          throw t,
          v =>
          v.toInt should be (5000)
        )
      }

      Ø.linkP(E)(alive)(socket =>
        proc.through(Ø.write(socket))).runFoldMap(identity).run
      ()
    }
  }
}
