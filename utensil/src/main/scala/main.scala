package intelmedia.ws
package funnel 

import scopt.{OptionParser,Read}
import java.net.URL
import scala.concurrent.duration._

case class Options(
  urls: Seq[(URL,String)], 
  aggregationInterval: Duration = 5 minutes,
  retryLimit: Int = 3,
  healthKey: String = "now/health")

trait FunnelCommandLine {
  // takes strings of the form:
  // accounts@http://foobar.com
  private def splitBucketURLPairs(str: String): (URL,String) = 
    str.split('@') match {
      case Array(bucket,urlstring) => (new URL(urlstring),bucket)
      case _ => sys.error(s"Unable to parse the supplied bucket@url pair: $str")
    }

  implicit val scoptReadUrl: Read[(URL,String)] = Read.reads { splitBucketURLPairs(_) }
  implicit val scoptReadDuration: Read[Duration] = Read.reads { Duration(_) }

  // protected val build = new BuildData
  protected val parser = new OptionParser[Options]("funnel"){
    head("funnel", "1.0")
    
    opt[Duration]('i',"interval").action { (duration, opts) =>
      opts.copy(aggregationInterval = duration)
    }

    opt[Int]('r',"retries").action { (limit, opts) => 
      opts.copy(retryLimit = limit)
    }

    arg[(URL,String)]("<url>[,url]...") unbounded() optional() action { (url, opts) =>
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
      println(">>>> "+ options)

      MonitoringServer.start(Monitoring.default, 5775)

      val health = Key[Boolean](options.healthKey, Units.Healthy)
      val events = Events.takeEvery(options.aggregationInterval, options.retryLimit)

      mirrorAndAggregate(events)(Process.emitAll(options.urls), health) {
        case "accounts" => Policies.quorum(2)
        case "test"     => Policies.majority
        case _          => sys.error("unknown group type")
      }.run

      println
      println("Press [Enter] to stop the funnel...")
      println

      readLine()
    }
} 
