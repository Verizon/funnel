package intelmedia.ws
package funnel

import riemann.Riemann
import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.Task
import scalaz.stream.Process
import scala.concurrent.duration._

object Utensil extends CLI {
  def main(args: Array[String]): Unit = {
    run(args){ options =>
      
      val M = Monitoring.default

      val M2 = Monitoring.instance()

      // startup the http monitoring server
      val shutdown = MonitoringServer.start(M, 4000)

      val shutdown2 = MonitoringServer.start(M2, 5775)

      val R = RiemannClient.tcp(options.riemann.host, options.riemann.port)
      R.connect() // urgh. Give me stregth! 

      val stop = new java.util.concurrent.atomic.AtomicBoolean(false)

      import instruments._

      val c = counter("requests")
      val t = timer("response-time")
    
      val t1 = Process.awakeEvery(2 seconds).map { _ =>
             c.increment
             t.time(Thread.sleep(100))
           }.run.runAsyncInterruptibly(println, stop)


      // M.mirrorStream.evalMap { case (url,group) => 
      //   println(">>>>>>>>>>>>> " + url)

      //   Riemann.mirrorAndPublish(M)(R)(SSE.readEvents)(Process.emit((url, group)))
      // }.run.runAsyncInterruptibly(println, stop)


      // M2.attemptMirrorAll(SSE.readEvents)(Events.every(1 minute))(
      //   new java.net.URL("http://localhost:4000/stream"),
      //   "p4000/" + _
      // ).run.runAsyncInterruptibly(println,stop)

      Riemann.mirrorAndPublish(M2)(R)(SSE.readEvents)(
        Process.emit((new java.net.URL("http://127.0.0.1:4000/stream"), "foo"))
          ).runAsyncInterruptibly(println, stop)

      println
      println("Press [Enter] to quit...")
      println

      readLine()

      shutdown2()
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

