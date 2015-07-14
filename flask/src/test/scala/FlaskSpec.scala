package funnel
package flask

import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.std.option._
import scalaz.syntax.applicative._
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scalaz.stream.Process
import Process._
import scalaz.stream.async.signalOf
import scalaz.stream.time.{ awakeEvery, sleep }
import scalaz.stream.wye
import argonaut._, Argonaut._
import journal.Logger
import funnel.elastic._
import funnel.http.JSON._
import funnel.http.MonitoringServer
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

  val flaskUrl = url(s"http://localhost:${options.funnelPort}/mirror").setContentType("application/json", "UTF-8")

  val S = Strategy.Executor(Monitoring.serverPool)
  val P = Monitoring.schedulingPool

  val log = Logger[this.type]

  private def makeMS(port: Int): MonitoringServer = {
    val L = ((s: String) => log.debug(s))
    val M = Monitoring.instance(Monitoring.serverPool, L)
    val I = new Instruments(1.minute, M, 200.milliseconds)
    val C = I.counter("my_counter", 0, "My counter")
    awakeEvery(100.milliseconds)(S, P).map(_ => C.increment).run.run
    MonitoringServer.start(M, port, 36.hours)
  }

  if (Ø.isEnabled) {
    "mirrorDatapoints with 5000 datapoints input" should "be 5000" in {
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

      val ready = signalOf(false)(Strategy.Executor(Monitoring.serverPool))

      implicit val B = scalaz.std.anyVal.booleanInstance.conjunction
      implicit val s = scalaz.stream.DefaultScheduler

      val E = Endpoint.unsafeApply(publish &&& bind, Settings.tcp)

      implicit val batransport: Transportable[Array[Byte]] = Transportable { ba =>
        Transported(Schemes.unknown, Versions.v1, None, None, ba)
      }

      val seq: Seq[Array[Byte]] = for(i <- 0 until 5000) yield Datapoint(Key[Double]("now/life", Units.Count: Units, "description", Map("url" -> "http://localhost")), 42.0).asJson.spaces2.getBytes
      val k: Seq[Boolean] = seq.map(_ => true) ++ Seq(false)

      val proc: Process[Task, Array[Byte]] = Process.emitAll(seq) fby Process.eval_(ready.set(true))
      val alive: Process[Task, Boolean] = Process.emitAll(k)

      val app = new Flask(options, new Instruments(1.minute))

      app.run(Array())
      Http(flaskUrl << payload OK as.String)(concurrent.ExecutionContext.Implicits.global)

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

      app.S.stop()
    }
  }

  "mirrorDatapoints for 1 second with no input" should "be 0" in {
    val ms = (0 until 100).map((i: Int) => makeMS(i + 1000))
    val payload = s"""
    [
      {
        "cluster": "datapoints-1.0-us-east",
        "urls": [
	  ${(1000 until 1100).map("\"http://localhost:" + _ + "/stream/now\"").mkString(",")}
        ]
      }
    ]
    """

    val app = new Flask(options, new Instruments(1.minute))
    app.run(Array())
    Http(flaskUrl << payload OK as.String)(concurrent.ExecutionContext.Implicits.global)
    val dps = app.I.monitoring.get(app.mirrorDatapoints.keys.now)
    val window = sleep(1.second)(S, P).fby(emit(true)).wye(dps.continuous)(wye.interrupt)(S)
    val count = window.runLast.run
    count shouldBe Some(0.0)
    ms.foreach(_.stop)
    app.S.stop()
  }
}
