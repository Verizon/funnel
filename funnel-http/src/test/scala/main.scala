package intelmedia.ws.monitoring

import java.net.URL
import scala.concurrent.duration._
import scalaz.stream.Process

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

  // val k = mirror[Double]("http://localhost:8082", "now/requests", Some("now/requests-clone"))
  M2.mirrorAll(SSE.readEvents)(
    new URL("http://localhost:8083/stream"),
    "node1/" + _
  ).run.runAsync(_ => ())
  M2.decay("node1")(Events.every(10 seconds)).run

  //
  // application that given a Process[Task,URL], mirr
  // def mirrorAll(nodes: Process[Task,(Int,URL)])(health: (Int, List[Key[Boolean]]) => Metric[Boolean]): Unit = ???

  // mirrorAll("http://node1:8081/now", Some("node1/"))
  // mirrorAll("http://node2:8081/now", Some("node1/"))
  // mirrorAll("http://node3:8081/now", Some("node1/"))

  // k.map(_ * 10).publishEvery(5 seconds)("now/request-clone-times-10", Units.Count)
  readLine()
}