package funnel
package integration

import java.net.URI
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.std.option._
import scalaz.syntax.applicative._
import scalaz.stream._
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import elastic.ElasticCfg
import flask.{ Flask, Options, RiemannCfg }
import journal.Logger
import http.MonitoringServer
import chemist.{ FlaskID, Location, LocationIntent, LocationTemplate, NetworkConfig, NetworkScheme, Sharding, StaticDiscovery }
import chemist.static.{ Static, StaticConfig, StaticChemist }
import zeromq.TCP
import http.Cluster
import http.JSON._
import argonaut._, Argonaut._

class ChemistSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
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
    val cluster          = cfg.lookup[String]("flask.cluster")
    val retriesDuration  = cfg.require[Duration]("flask.schedule.duration")
    val maxRetries       = cfg.require[Int]("flask.schedule.retries")
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
    val telemetry        = cfg.lookup[Int]("flask.network.telemetry-port").getOrElse(7390)
    val selfiePort       = cfg.lookup[Int]("flask.network.selfie-port").getOrElse(7557)
    val collectLocal     = cfg.lookup[Boolean]("flask.collect-local-metrics")
    val localFrequency   = cfg.lookup[Int]("flask.local-metric-frequency")

    Task((Options(name, cluster, retriesDuration, maxRetries, elastic, riemann, collectLocal, localFrequency, port, selfiePort, None, telemetry), cfg))
  }.run

  val flaskUrl = url(s"http://localhost:${options.funnelPort}/mirror").setContentType("application/json", "UTF-8")

  val log = Logger[this.type]

  private def makeMS(port: Int): (Counter, MonitoringServer) = {
    val L = ((s: String) => log.debug(s))
    val M = Monitoring.instance(Monitoring.serverPool, L)
    val I = new Instruments(1.minute, M, 200.milliseconds)
    val C = I.counter("my_counter", 0, "My counter")
    val ms = MonitoringServer.start(M, port, 36.hours)
    (C, ms)
  }

  "Chemist" should "bootstrap successfully" in {
    val n = 100
    val ms = (1024 until 1024 + n).map(makeMS)
    val payload = s"""
    [
      {
        "cluster": "datapoints-1.0-us-east",
        "urls": [
          ${(1024 until 1024 + n).map(p => "\"http://localhost:" + p + "/stream/now\"").mkString(",\n")}
        ]
      }
    ]
    """

    val S = Strategy.Executor(Monitoring.serverPool)
    val P = Monitoring.schedulingPool

    val templates = List(LocationTemplate("http://@host:@port/stream/previous"))

    val app = new Flask(options, new Instruments(1.minute))

    app.run(Array())
    Http(flaskUrl << payload OK as.String)(concurrent.ExecutionContext.Implicits.global)
    Thread.sleep(1000)

    val F = chemist.Flask(
      FlaskID(options.name.get),
      Location("localhost", options.funnelPort, "local", NetworkScheme.Http, false, LocationIntent.Mirroring, templates),
      Location("localhost", options.telemetryPort, "local", NetworkScheme.Zmtp(TCP), false, LocationIntent.Supervision, templates)
    )

    val cfg = StaticConfig(NetworkConfig("127.0.0.1", 64529), 1.second, Map.empty, Map(F.id -> F)) 
    class DefaultStatic extends Static {
      def config = cfg
    }
    val platform = new DefaultStatic
    val C = new StaticChemist[DefaultStatic]

    val d: Map[FlaskID, Map[ClusterName, List[URI]]] = (for {
      _   <- C.init
      _   <- C.bootstrap
      dis <- C.distribution
    } yield dis).run(platform).run

    val src = Http(flaskUrl / "sources" OK as.String)(concurrent.ExecutionContext.Implicits.global)()
    val s = src.decodeOption[List[Cluster]].get

    app.S.stop()
    app.SelfServing.stop()
    ms.foreach(s => s._2.stop())

    d should have size 1
    d.keys.head shouldBe (F.id)
    s should have size 1
    val d2 = d.values
    d2 should have size 1
    val d3 = d2.head.toList.map(p => (p._1, p._2.toSet))
    d3 should have size 1
    val distribution: (String, Set[URI]) = d3.head
    val sources: (String, Set[URI]) = (s.head.label, s.head.urls.map(u => new URI(u)).toSet)
    distribution should equal (sources)
  }
}
