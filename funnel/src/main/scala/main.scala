package intelmedia.ws
package monitoring 

import scopt.{OptionParser,Read}
import java.net.URL
import scala.concurrent.duration._

case class Options(urls: Seq[URL], duration: Duration = 5 minutes)

trait FunnelCommandLine {
  implicit val scoptReadUrl: Read[URL] = Read.reads { new URL(_) }
  implicit val scoptReadDuration: Read[Duration] = Read.reads { Duration(_) }

  // protected val build = new BuildData
  protected val parser = new OptionParser[Options]("funnel"){
    head("funnel", "1.0")
    
    opt[Duration]('d',"duration").action { (time, opts) =>
      opts.copy(duration = time)
    }.text("Duration is a required property")
    
    arg[URL]("<url>[,url]...") unbounded() optional() action { (url, opts) =>
      opts.copy(urls = opts.urls :+ url) } text("optional unbounded args")
  }

  def run(args: Array[String])(f: Options => Unit): Unit = 
    parser.parse(args, Options(Seq.empty)).foreach(f)
}

import Monitoring.default.mirrorAndAggregate
import scalaz.stream.Process

object Funnel extends FunnelCommandLine {
  def main(args: Array[String]): Unit = 
    run(args){ options =>  
      val health = Key[Boolean]("now/health", Units.Healthy)

      mirrorAndAggregate(Events.takeEvery(options.duration, 5))(Process.emitAll(options.urls), health) {
        case "accounts" => Policies.quorum(2)
        case "decoding" => Policies.majority
        case _          => sys.error("unknown group type")
      }.run
    }
} 
