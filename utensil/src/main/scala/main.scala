package intelmedia.ws
package funnel

import riemann.Riemann

object Utensil extends CLI {
  def main(args: Array[String]): Unit = {
    run(args){ options =>
      val shutdown = MonitoringServer.start(Monitoring.default, options.funnelPort)

      println
      println("Press [Enter] to stop the Funnel utensil...")
      println

      readLine()

      shutdown()
    }
  }
} 

import java.net.URL
import scopt.{OptionParser,Read}
import scala.concurrent.duration._

case class RiemannSettings(host: String, port: Int)

case class Options(
  riemann: RiemannSettings = RiemannSettings("localhost",5555),
  funnelPort: Int = 5775
)

trait CLI {
  implicit val scoptReadUrl: Read[RiemannSettings] = 
    Read.reads { str =>
      str.split(':') match {
        case Array(host,port) => RiemannSettings(host,port.toInt) // ok to explode here
        case _ => sys.error("The supplied host:port combination for the riemann server are not valid.")
      }
    }
  implicit val scoptReadDuration: Read[Duration] = Read.reads { Duration(_) }

  protected val parser = new OptionParser[Options]("funnel"){
    head("funnel", "1.0")

    opt[RiemannSettings]('r',"riemann").action { (rs, opts) => 
      opts.copy(riemann = rs)
    }
  }

  def run(args: Array[String])(f: Options => Unit): Unit = 
    parser.parse(args, Options()).foreach(f)
}

