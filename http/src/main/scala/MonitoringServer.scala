package intelmedia.ws.funnel
package http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.{BufferedWriter, IOException, OutputStream, OutputStreamWriter}
import java.net.{InetSocketAddress, URL}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream._

object MonitoringServer {

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
  def start(M: Monitoring, port: Int = 8080): MonitoringServer = {
    val svr = (new MonitoringServer(M, port))
    svr.start()
    svr
  }

  @deprecated("""MonitoringServer.start no longer takes a `log` argument.
    Use Monitoring.instance to create your Monitoring instance
    if you want to specify a logger.""")
  def start(M: Monitoring, port: Int, log: String => Unit): MonitoringServer = start(M, port)

  @deprecated("""MonitoringServer.start no longer takes a `log` argument.
    Use Monitoring.instance to create your Monitoring instance
    if you want to specify a logger.""")
  def start(M: Monitoring, log: String => Unit): MonitoringServer = start(M)
}

class MonitoringServer(M: Monitoring, port: Int) extends ControlServer {
  private[funnel] val (mirroringQueue,sourcesToMirror) =
    async.queue[(URL,String)](Strategy.Executor(Monitoring.serverPool))

  private[funnel] val (terminatingQueue,sourcesToTerminate) =
    async.queue[URL](Strategy.Executor(Monitoring.serverPool))

  private val server = HttpServer.create(new InetSocketAddress(port), 0)

  def start(): Unit = {
    server.setExecutor(Monitoring.serverPool)
    val _ = server.createContext("/", handleMetrics(M))
    server.start()
    M.log("server started on port: " + port)
  }

  def stop(): Unit = server.stop(0)

  protected def handleIndex(req: HttpExchange): Unit = {
    req.sendResponseHeaders(200, helpHTML.length)
    req.getResponseBody.write(helpHTML.getBytes)
  }

  protected def handleStream(M: Monitoring, prefix: String, req: HttpExchange): Unit = {
    req.getResponseHeaders.set("Content-Type", "text/event-stream")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
    val events = Monitoring.subscribe(M)(Key.StartsWith(prefix))
    val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
    SSE.writeEvents(events, sink)
  }

  protected def handleKeys(M: Monitoring, prefix: String, req: HttpExchange): Unit = {
    import JSON._; import argonaut.EncodeJson._
    val ks = M.keys.continuous.once.runLastOr(List()).run.filter(_.startsWith(prefix))
    val respBytes = JSON.prettyEncode(ks).getBytes
    req.getResponseHeaders.set("Content-Type", "application/json")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, respBytes.length)
    req.getResponseBody.write(respBytes)
  }

  protected def handleKeysStream(M: Monitoring, req: HttpExchange): Unit = {
    req.getResponseHeaders.set("Content-Type", "text/event-stream")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
    val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
    SSE.writeKeys(M.distinctKeys, sink)
  }

  protected def handleNow(M: Monitoring, label: String, req: HttpExchange): Unit = {
    import JSON._; import argonaut.EncodeJson._
    val m = Monitoring.snapshot(M).run
    val respBytes =
      JSON.prettyEncode(m.filterKeys(_.startsWith(label)).values.toList).getBytes
    req.getResponseHeaders.set("Content-Type", "application/json")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, respBytes.length)
    req.getResponseBody.write(respBytes)
  }

  protected def handleAddMirroringURLs(M: Monitoring, req: HttpExchange): Unit = {
    import JSON._; import argonaut.Parse; import scala.io.Source

    if(req.getRequestMethod.toLowerCase == "post"){
      // as the payloads here will be small, lets just turn it into a string
      val json = Source.fromInputStream(req.getRequestBody).mkString
      Parse.decodeEither[List[Bucket]](json).fold(
        error => flush(400, error.toString, req),
        blist => {
          blist.flatMap(b => b.urls.map(u => new URL(u) -> b.label)
            ).foreach(mirroringQueue.enqueue)

          flush(202, Array.empty[Byte], req)
        }
      )
    } else flush(405, "Request method not allowed.", req)
  }

  private def flush(status: Int, body: String, req: HttpExchange): Unit =
    flush(status, body.getBytes, req)

  private def flush(status: Int, body: Array[Byte], req: HttpExchange): Unit = {
    req.sendResponseHeaders(status,body.length)
    req.getResponseBody.write(body)
  }

  protected def handleMetrics(M: Monitoring) = new HttpHandler {
    def handle(req: HttpExchange): Unit = try {
      M.log("path: " + req.getRequestURI.getPath)
      val path = req.getRequestURI.getPath match {
        case "/" => Nil
        case p   => p.split("/").toList.tail
      }
      path match {
        case Nil                       => handleIndex(req)
        case "mirror" :: Nil           => handleAddMirroringURLs(M, req)
        case "keys" :: tl              => handleKeys(M, tl.mkString("/"), req)
        case "stream" :: "keys" :: Nil => handleKeysStream(M, req)
        case "stream" :: tl            => handleStream(M, tl.mkString("/"), req)
        case now                       => handleNow(M, now.mkString("/"), req)
      }
    }
    catch {
      case e: Exception => M.log("fatal error: " + e)
    }
    finally req.close
  }

  val helpHTML = """
  |<html>
  |<body>
  |<p>Monitoring resources:</p>
  |<ul>
  |<li><a href="/keys">/keys</a>: Current snapshot of all metric keys.</li>
  |<li><a href="/keys/id">/keys</a>: Current snapshot of all keys prefixed by 'id'.</li>
  |<li><a href="/now">/now</a>: Current values for all metrics prefixed by 'now'. </li>
  |<li><a href="/previous">/previous</a>: Current values for all metrics prefixed by 'previous'.</li>
  |<li><a href="/sliding">/sliding</a>: Current values for all metrics prefixed by 'sliding'.</li>
  |<li><a href="/stream/keys">/stream/keys</a>: Full stream of all metric keys.</li>
  |<li><a href="/stream">/stream</a>: Full stream of all metrics.</li>
  |</ul>
  |</body>
  |</html>
  """.stripMargin

}
