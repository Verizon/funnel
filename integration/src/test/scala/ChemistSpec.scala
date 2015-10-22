// package funnel
// package integration

// import java.net.URI
// import scala.concurrent.duration._
// import scalaz.concurrent.{Strategy,Task}
// import scalaz.std.option._
// import scalaz.syntax.applicative._
// import scalaz.stream._
// import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
// import elastic.ElasticCfg
// import flask.{Flask,Options}
// import journal.Logger
// import http.MonitoringServer
// import chemist.{ FlaskID, Location, LocationIntent, LocationTemplate, NetworkConfig, NetworkScheme, Sharding, StaticDiscovery }
// import chemist.static.{ Static, StaticConfig, StaticChemist }
// import zeromq.TCP
// import http.Cluster
// import http.JSON._
// import argonaut._, Argonaut._

// class ChemistSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
//   import dispatch._

//   val options = Options(
//     name = Some("flask"),
//     cluster = Some("local-flask"),
//     retriesDuration = 30.seconds,
//     maxRetries = 6,
//     elasticExploded = None,
//     elasticFlattened = None,
//     collectLocalMetrics = Some(true),
//     localMetricFrequency = Some(30),
//     funnelPort = 6777,
//     selfiePort = 7557,
//     metricTTL = None,
//     environment ="test")

//   val flaskUrl = url(s"http://localhost:${options.funnelPort}/mirror").setContentType("application/json", "UTF-8")

//   val log = Logger[this.type]

//   private def makeMS(port: Int): (Counter, MonitoringServer) = {
//     val L = ((s: String) => log.debug(s))
//     val M = Monitoring.instance(Monitoring.serverPool, L)
//     val I = new Instruments(1.minute, M, 200.milliseconds)
//     val C = I.counter("my_counter", 0, "My counter")
//     val ms = MonitoringServer.start(M, port, 36.hours)
//     (C, ms)
//   }

//   "Chemist" should "bootstrap successfully" in {
//     val n = 100
//     val ms = (1024 until 1024 + n).map(makeMS)
//     val payload = s"""
//     [
//       {
//         "cluster": "datapoints-1.0-us-east",
//         "urls": [
//           ${(1024 until 1024 + n).map(p => "\"http://localhost:" + p + "/stream/now\"").mkString(",\n")}
//         ]
//       }
//     ]
//     """

//     val S = Strategy.Executor(Monitoring.serverPool)
//     val P = Monitoring.schedulingPool

//     val templates = List(LocationTemplate("http://@host:@port/stream/previous"))

//     val app = new Flask(options, new Instruments(1.minute))

//     app.unsafeRun()
//     Http(flaskUrl << payload OK as.String)(concurrent.ExecutionContext.Implicits.global)
//     Thread.sleep(1000)

//     val F = chemist.Flask(
//       FlaskID(options.name.get),
//       Location("localhost", options.funnelPort, "local", NetworkScheme.Http, LocationIntent.Mirroring, templates)
//     )

//     val cfg = StaticConfig(
//       network = NetworkConfig("127.0.0.1", 64529),
//       commandTimeout = 1.second,
//       targets = Map.empty,
//       flasks = Map(F.id -> F),
//       state = chemist.MemoryStateCache
//     )

//     class DefaultStatic extends Static {
//       def config = cfg
//     }

//     val platform = new DefaultStatic

//     val C = new StaticChemist[DefaultStatic]

//     val d: Map[Flask, Map[ClusterName, List[URI]]] = (for {
//       _   <- C.init
//       dis <- C.distribution
//     } yield dis).run(platform).run

//     val src = Http(flaskUrl / "sources" OK as.String)(concurrent.ExecutionContext.Implicits.global)()
//     val s = src.decodeOption[List[Cluster]].get

//     app.S.stop()
//     app.SelfServing.stop()
//     ms.foreach(s => s._2.stop())

//     d should have size 1
//     d.keys.head shouldBe (F.id)
//     // Size 2 because of selfie
//     s should have size 2
//     val d2 = d.values
//     d2 should have size 1
//     val d3 = d2.head.toList.map(p => (p._1, p._2.toSet))
//     d3 should have size 1
//     val distribution: (String, Set[URI]) = d3.head
//     val sources: Set[(String, Set[URI])] = s.map(x => (x.label, x.urls.map(u => new URI(u)).toSet)).toSet
//     sources should contain (distribution)
//   }
// }
