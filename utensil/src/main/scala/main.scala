package intelmedia.ws
package funnel

import riemann.Riemann
import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.Task
import scalaz.stream.Process

object Utensil extends CLI {
  def main(args: Array[String]): Unit = {
    run(args){ options =>
      val M = Monitoring.default

      // startup the http monitoring server
      val shutdown = MonitoringServer.start(M, options.funnelPort)

      val R = RiemannClient.tcp(options.riemann.host, options.riemann.port)
      R.connect() // urgh. Give me stregth! 

      // Riemann.publish(Monitoring.default)(R)

      M.mirrorStream.evalMap { case (url,group) => 
        println(">>>>>>>>>>>>> " + url)

        Riemann.mirrorAndPublish(M)(R)(SSE.readEvents)(Process.emit((url, group)))
      }.run.runAsync(_ => ())

      println
      println("Press [Enter] to stop the Funnel utensil...")
      println

      readLine()

      shutdown()
      if(R.isConnected) R.disconnect else ()
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
    head("funnel", "1.0")

    opt[RiemannSettings]('r',"riemann").action { (rs, opts) => 
      opts.copy(riemann = rs)
    }
    
    opt[Int]('p', "http-port").action { (p, opts) => 
      opts.copy(funnelPort = p)
    }

    /** provide flexibilty to swap out the sse stream later with something else **/
    opt[DatapointParser]('t', "transport").action { (t, opts) =>
      opts.copy(transport = t)
    }
  }

  def run(args: Array[String])(f: Options => Unit): Unit = 
    parser.parse(args, Options()).foreach(f)
}

