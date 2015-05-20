package funnel
package http
package examples

import java.net.URL
import org.scalacheck._
import Prop._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.stream.Process

object MirroringExample {

  implicit val log = (s: String) => { println(s) }

  def main(args: Array[String]): Unit = {

    val health = Key[String]("now/health", Units.TrafficLight)

    implicit val P = Monitoring.schedulingPool

    /**
     * Generate some bogus activity for a `Monitoring` instance,
     * and die off after about 5.minutes.
     */
    def activity(M: Monitoring, name: String, port: Int): Unit = {
      val ttl = (math.random * 60).toInt + 30
      val I = new Instruments(5.minutes, M)
      val ok = I.trafficLight("health")
      val reqs = I.counter("reqs")
      val svr = MonitoringServer.start(M, port)

      Process.awakeEvery(2.seconds)(Strategy.Executor(Monitoring.serverPool), Monitoring.schedulingPool).takeWhile(_ < (ttl.seconds)).map { _ =>
        reqs.incrementBy((math.random * 10).toInt)
        ok.green
      }.onComplete(Process.eval_(
        Task.delay { println(s"halting $name:$port"); svr.stop() })).run.runAsync(_ => ())
    }

    val accountCluster = (1 to 3).map { i =>
      val port = 8080 + i
      activity(Monitoring.instance, "accounts", port)
      ("http://localhost:"+port+"/stream", "accounts")
    }
    val decodingCluster = (1 to 5).map { i =>
      val port = 9080 + i
      activity(Monitoring.instance, "decoding", port)
      ("http://localhost:"+port+"/stream", "decoding")
    }

    val urls: Process[Task, (URL,String)] = // cluster comes online gradually
      Process.emitAll(accountCluster ++ decodingCluster).flatMap {
        case (url,group) => Process.sleep(2.seconds)(Strategy.Executor(Monitoring.serverPool), Monitoring.schedulingPool) ++
                            Process.emit(new URL(url) -> group)
      }

    val M = Monitoring.instance
    val _ = MonitoringServer.start(M, 8000)
    ()
  }
}
