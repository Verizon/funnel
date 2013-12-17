package intelmedia.ws.funnel
package utensil

import riemann.Riemann
import http.{MonitoringServer,SSE}
import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.Task
import scalaz.stream.Process
import scala.concurrent.duration._

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
      
      val M = Monitoring.default
      val S = MonitoringServer.start(M, options.funnelPort)

      val R = RiemannClient.tcp(options.riemann.host, options.riemann.port)
      try {
        R.connect() // urgh. Give me stregth! 
      } catch {
        case e: java.io.IOException => {
          errorAndQuit(options,() => shutdown(S,R))
        }
      }

      S.mirroringSources.evalMap { case (url,bucket) =>
       Riemann.mirrorAndPublish(M)(R)(SSE.readEvents)(
          Process.emit((url, bucket)))
      }.run.runAsyncInterruptibly(println, stop)

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

  case class RiemannSettings(host: String, port: Int)

  case class Options(
    riemann: RiemannSettings = RiemannSettings("localhost",5555),
    funnelPort: Int = 5775,
    transport: DatapointParser = SSE.readEvents _
  )

  implicit val scoptReadUrl: Read[RiemannSettings] = 
    Read.reads { str =>
      str.split(':') match {
        case Array(host,port) => RiemannSettings(host,port.toInt) // ok to explode here
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

    opt[RiemannSettings]('r',"riemann").action { (rs, opts) => 
      opts.copy(riemann = rs)
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

