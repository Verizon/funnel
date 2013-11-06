package intelmedia.ws.monitoring

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.{BufferedWriter, IOException, OutputStream, OutputStreamWriter}
import java.net.InetSocketAddress
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream._

object Main extends App {
  MonitoringServer.start(Monitoring.default, 8081)

  import Instruments.default._
  val c = counter("requests")
  val t = timer("response-time")
  val g = Process.awakeEvery(2 seconds).map { _ =>
    c.increment
    t.time(Thread.sleep(100))
  }.run.run
}

object MonitoringServer {

  type Log = String => Unit

  /**
   * `/`: self-describing list of available resources
   * `/keys`: stream of changing list of keys
   * `/now`: snapshot of all metrics whose labels begin with 'now'
   * `/previous`: snapshot of all metrics whose labels begin with 'previous'
   * `/sliding`: snapshot of all metrics whose labels begin with 'sliding'
   * `/<prefix>`: snapshot of all metrics whose labels begin with 'prefix' (except for 'stream' and 'keys', which are reserved)
   * `/stream`: stream of all metrics
   * `/stream/keys`: stream of changing list of keys
   * `/stream/<keyid>`: stream of metrics for the given key
   * `/stream/<prefix>`: stream of metrics whose labels start with 'prefix'
   */
  def start(M: Monitoring, port: Int = 8080, log: Log = println): Unit = {
    val server = HttpServer.create(new InetSocketAddress(port), 0)
    server.setExecutor(Monitoring.serverPool)
    server.createContext("/", handler(M, log))
    server.start()
    log("server started on port: " + port)
  }

  private[monitoring] def handler(M: Monitoring, log: Log) = new HttpHandler {
    def handle(req: HttpExchange): Unit = try {
      log("path: " + req.getRequestURI.getPath)
      val path = req.getRequestURI.getPath match {
        case "/" => List()
        case p => p.split("/").toList.tail
      }
      log("split path: " + path)
      path match {
        case Nil => handleRoot(req)
        case "keys" :: Nil => handleKeys(M, req, log)
        case "stream" :: "keys" :: Nil => handleKeysStream(M, req, log)
        case "stream" :: tl => handleStream(M, tl.mkString("/"), req, log)
        case now => handleNow(M, now.mkString("/"), req, log)
      }
    }
    catch {
      case e: Exception => log("fatal error: " + e)
    }
    finally req.close

    def handleKeys(M: Monitoring, req: HttpExchange, log: Log): Unit = {
      val ks = M.keys.continuous.once.runLastOr(List()).run
      val respBytes = Output.toJSON(ks).toString.getBytes
      req.getResponseHeaders.set("Content-Type", "application/json")
      req.sendResponseHeaders(200, respBytes.length)
      req.getResponseBody.write(respBytes)
    }

    def handleKeysStream(M: Monitoring, req: HttpExchange, log: Log): Unit = {
      req.getResponseHeaders.set("Content-Type", "text/event-stream")
      req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
      val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
      Output.keysToSSE(M.distinctKeys, sink)
    }

    def handleStream(M: Monitoring, prefix: String, req: HttpExchange, log: Log): Unit = {
      req.getResponseHeaders.set("Content-Type", "text/event-stream")
      req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
      val events = Monitoring.subscribe(M)(prefix, log)
      val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
      Output.eventsToSSE(events, sink)
    }

    def handleNow(M: Monitoring, label: String, req: HttpExchange, log: Log): Unit = {
      val m = Monitoring.snapshot(M).run
      val resp = Output.toJSON(m.filterKeys(_.matches(label))).toString
      val respBytes = resp.getBytes
      log("response: " + resp)
      req.getResponseHeaders.set("Content-Type", "application/json")
      req.sendResponseHeaders(200, respBytes.length)
      req.getResponseBody.write(respBytes)
    }

    def handleRoot(req: HttpExchange): Unit = {
      req.sendResponseHeaders(200, helpHTML.length)
      req.getResponseBody.write(helpHTML.getBytes)
    }
  }

  val helpHTML = """
  |<html>
  |<body>
  |<p>Monitoring resources:</p>
  |<ul>
  |<li><a href="/now">/now</a>: Current snapshot of all metrics with labels prefixed by 'now'. </li>
  |<li><a href="/previous">/previous</a>: Current snapshot of all metrics with labels prefixed by 'previous'.</li>
  |<li><a href="/sliding">/sliding</a>: Current snapshot of all metrics with labels prefixed by 'previous'.</li>
  |<li><a href="/stream">/stream</a>: Full stream of all metrics.</li>
  |<li><a href="/stream/keys">/keys</a>: Full stream of all metric keys.</li>
  |<li><a href="/stream/id">/keys/id</a>: Full stream of metrics with the given key.</li>
  |</ul>
  |</body>
  |</html>
  """.stripMargin

}
