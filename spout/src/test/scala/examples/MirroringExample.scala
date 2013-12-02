package intelmedia.ws.monitoring
package examples

import java.net.URL
import org.scalacheck._
import Prop._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process

object MirroringExample extends Properties("mirroring") {

  property("example") = secure {

    val health = Key[Boolean]("now/health", Units.Healthy)

    /**
     * Generate some bogus activity for a `Monitoring` instance,
     * and die off after about 5 minutes.
     */
    def activity(M: Monitoring, name: String, port: Int): Unit = {
      val ttl = (math.random * 60).toInt + 30
      val I = new Instruments(5 minutes, M)
      val ok = I.gauge("health", true, Units.Healthy)
      val reqs = I.counter("reqs")
      val kill = MonitoringServer.start(M, port)
      Process.awakeEvery(2 seconds).takeWhile(_ < (ttl seconds)).map { _ =>
        reqs.incrementBy((math.random * 10).toInt)
        ok.set(true)
      }.onComplete(Process.eval_(
        Task.delay { println(s"halting $name:$port"); kill() })).run.runAsync(_ => ())
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
      Process.emitSeq(accountCluster ++ decodingCluster).flatMap {
        case (url,group) => Process.sleep(2 seconds) ++
                            Process.emit(new URL(url) -> group)
      }

    val M = Monitoring.instance
    MonitoringServer.start(M, 8000)

    M.mirrorAndAggregate(Events.takeEvery(1 minutes, 5))(urls, health) {
      case "accounts" => Policies.quorum(2)
      case "decoding" => Policies.majority
      case _          => sys.error("unknown group type")
    }.run

    true
  }
}
