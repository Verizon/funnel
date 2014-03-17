package intelmedia.ws.funnel
package utensil

import riemann.Riemann
import http.{MonitoringServer,SSE}
import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.Task
import scalaz.stream.Process
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

/**
  * How to use:
  *
  * utensil \
  *   --riemann localhost:5555 \
  *   --transport http-sse \
  *   --port 5775
  *
  * utensil -r localhost:5555 -t http-sse -p 5775
  */
object Utensil extends CLI {
  private val stop = new java.util.concurrent.atomic.AtomicBoolean(false)
  private def shutdown(server: MonitoringServer, R: RiemannClient): Unit = {
    server.stop()
    // nice little hack to get make it easy to just hit return and shutdown
    // this running example
    stop.set(true)
    R.disconnect
  }

  private def errorAndQuit(options: Options, f: () => Unit): Unit = {
    val msg = s"# Riemann is not running at the specified location (${options.riemann.host}:${options.riemann.port}) #"
    val padding = (for(_ <- 1 to msg.length) yield "#").mkString
    Console.err.println(padding)
    Console.err.println(msg)
    Console.err.println(padding)
    f()
    System.exit(1)
  }

  def main(args: Array[String]): Unit = {
    run(args){ options =>

      val L = LoggerFactory.getLogger("utensil")
      implicit val log: String => SafeUnit = s => L.info(s)

      val M = Monitoring.default
      val S = MonitoringServer.start(M, options.funnelPort, log)

      val R = RiemannClient.tcp(options.riemann.host, options.riemann.port)
      try {
        R.connect() // urgh. Give me stregth!
      } catch {
        case e: java.io.IOException => {
          errorAndQuit(options,() => shutdown(S,R))
        }
      }

      Riemann.mirrorAndPublish(M, options.riemannTTL.toSeconds.toFloat)(R)(SSE.readEvents)(S.mirroringSources)(log)
      .runAsyncInterruptibly(println, stop)

      println
      println("Press [Enter] to quit...")
      println

      readLine()

      shutdown(S,R)
    }
  }

}

import java.net.URL
import scopt.{OptionParser,Read}
import scala.concurrent.duration._

trait CLI {

  case class RiemannHostPort(host: String, port: Int)

  case class Options(
    riemann: RiemannHostPort = RiemannHostPort("localhost", 5555),
    riemannTTL: Duration = 5 minutes,
    funnelPort: Int = 5775,
    transport: DatapointParser = SSE.readEvents _
  )

  implicit val scoptReadUrl: Read[RiemannHostPort] =
    Read.reads { str =>
      str.split(':') match {
        case Array(host,port) => RiemannHostPort(host,port.toInt) // ok to explode here
        case _ => sys.error("The supplied host:port combination for the riemann server are not valid.")
      }
    }

  implicit val scoptDpParser: Read[DatapointParser] =
    Read.reads { str =>
      str match {
        case "http-sse" => SSE.readEvents _
        case _ => sys.error(s"The supplied ($str) transport does not have a corrosponding parser.")
      }
    }

  implicit val scoptReadDuration: Read[Duration] = Read.reads { Duration(_) }

  protected val parser = new OptionParser[Options]("funnel"){
    head("Funnel Utensil", "1.0")

    opt[RiemannHostPort]('r',"riemann").action { (rs, opts) =>
      opts.copy(riemann = rs)
    }

    opt[Duration]('e', "expiry").action { (e, opts) =>
      opts.copy(riemannTTL = e)
    }

    opt[Int]('p', "port").action { (p, opts) =>
      opts.copy(funnelPort = p)
    }

    /** provide flexibilty to swap out the sse stream later with something else **/
    opt[DatapointParser]('t', "transport").action { (t, opts) =>
      opts.copy(transport = t)
    }
  }

  def run(args: Array[String])(f: Options => Unit): Unit =
    parser.parse(args, Options()).map(f).getOrElse(())
}

