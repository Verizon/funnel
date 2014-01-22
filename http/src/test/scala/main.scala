package intelmedia.ws.funnel

import java.net.URL
import scala.concurrent.duration._
import scalaz.stream.Process

import http.{MonitoringServer,SSE}

object Main extends App {
  MonitoringServer.start(Monitoring.default, 8081)

  import instruments._
  val c = counter("requests")
  val t = timer("response-time")
  val g = Process.awakeEvery(2 seconds).map { _ =>
    c.increment
    t.time(Thread.sleep(100))
  }.run.runAsync(_ => ())

  val M = Monitoring.instance
  val M2 = Monitoring.instance
  val I = new Instruments(5 minutes, M)
  MonitoringServer.start(M, 8083)
  MonitoringServer.start(M2, 8082)

  val c2 = I.counter("requests")
  val c3 = I.counter("requests2")
  val h = I.gauge("health", true, Units.Healthy)
  val g2 = Process.awakeEvery(2 seconds).map { _ =>
    c2.increment; c3.increment; h.set(true)
  }.take(5).run.runAsync(_ => ())

  M2.mirrorAll(SSE.readEvents)(
    new URL("http://localhost:8083/stream"),
    "node1/" + _
  ).run.runAsync(_ => ())
  M2.decay(Key.StartsWith("node1"))(Events.every(10 seconds)).run
}
