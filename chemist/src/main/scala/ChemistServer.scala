package oncue.svc.funnel.chemist

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.{BufferedWriter, IOException, OutputStream, OutputStreamWriter}
import java.net.{InetSocketAddress, URL}
import journal.Logger
import intelmedia.ws.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Free}
import scalaz.concurrent.Task

object ChemistServer {
  private val log = Logger[Server]
}

class ChemistServer(I: Interpreter[Server.ServerF], port: Int){
  import ChemistServer.log
  import argonaut._
  import Argonaut._

  private val S = Server
  private val server = HttpServer.create(new InetSocketAddress(port), 0)

  def start(): Unit = {
    server.setExecutor(Server.serverPool)
    val _ = server.createContext("/", handler)
    server.start()
  }

  def stop(): Unit = server.stop(0)

  private def run[A : CodecJson](exe: Free[Server.ServerF, A], req: HttpExchange): Unit =
    I.run(exe).attemptRun match {
      case \/-(a) => {
        val out = a.asJson.nospaces // the `A` must be convertable to JSON
        req.sendResponseHeaders(200, out.length)
        req.getResponseBody.write(out.getBytes)
      }
      case -\/(e) => {
        req.sendResponseHeaders(500, e.toString.length)
        req.getResponseBody.write(e.toString.getBytes)
      }
    }

  protected def handleIndex(req: HttpExchange): Unit = {
    req.sendResponseHeaders(200, indexHTML.length)
    req.getResponseBody.write(indexHTML.getBytes)
  }

  protected def handleDistribute(req: HttpExchange): Unit = {
    req.sendResponseHeaders(200,0)
    req.getResponseBody.write("Nothing to see here yet.".getBytes)
  }

  protected def handleNotImplemented(req: HttpExchange): Unit = {
    req.sendResponseHeaders(501,0)
  }

  protected def handleStatus(req: HttpExchange): Unit =
    req.sendResponseHeaders(200,0)

  protected def handler = new HttpHandler {
    def handle(req: HttpExchange): Unit = try {
      val path = req.getRequestURI.getPath match {
        case "/" => Nil
        case p   => p.split("/").toList.tail
      }

      log.debug(s"http request for $path")

      path match {
        case Nil                    => handleIndex(req)
        case "status"        :: Nil => handleStatus(req)
        case "distribution"  :: Nil => run(S.distribution, req)
        case "distribute"    :: Nil => handleDistribute(req)
        case _                      => handleNotImplemented(req)
      }
    }
    catch {
      case e: Exception => log.error("fatal error: " + e)
    }
    finally req.close
  }

  val indexHTML = s"""
    |<!DOCTYPE html>
    |<html lang="en">
    |  <head>
    |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css">
    |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css">
    |    <script src="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"></script>
    |    <title>Chemist &middot; ${BuildInfo.version} &middot; ${BuildInfo.gitRevision}</title>
    |    <style type="text/css">
    |    /* Space out content a bit */
    |    body {
    |      padding-top: 20px;
    |      padding-bottom: 20px;
    |    }
    |
    |    /* Everything but the jumbotron gets side spacing for mobile first views */
    |    .header,
    |    .marketing,
    |    .footer {
    |      padding-right: 15px;
    |      padding-left: 15px;
    |    }
    |
    |    /* Custom page header */
    |    .header {
    |      border-bottom: 1px solid #e5e5e5;
    |    }
    |    /* Make the masthead heading the same height as the navigation */
    |    .header h3 {
    |      padding-bottom: 19px;
    |      margin-top: 0;
    |      margin-bottom: 0;
    |      line-height: 40px;
    |    }
    |
    |    /* Custom page footer */
    |    .footer {
    |      padding-top: 19px;
    |      color: #777;
    |      border-top: 1px solid #e5e5e5;
    |    }
    |
    |    /* Customize container */
    |    @media (min-width: 768px) {
    |      .container {
    |        max-width: 730px;
    |      }
    |    }
    |    .container-narrow > hr {
    |      margin: 30px 0;
    |    }
    |
    |    /* Main marketing message and sign up button */
    |    .jumbotron {
    |      text-align: center;
    |      border-bottom: 1px solid #e5e5e5;
    |    }
    |    .jumbotron .btn {
    |      padding: 14px 24px;
    |      font-size: 21px;
    |    }
    |
    |    /* Supporting marketing content */
    |    .marketing {
    |      margin: 40px 0;
    |    }
    |    .marketing p + h4 {
    |      margin-top: 28px;
    |    }
    |
    |    /* Responsive: Portrait tablets and up */
    |    @media screen and (min-width: 768px) {
    |      /* Remove the padding we set earlier */
    |      .header,
    |      .marketing,
    |      .footer {
    |        padding-right: 0;
    |        padding-left: 0;
    |      }
    |      /* Space out the masthead */
    |      .header {
    |        margin-bottom: 30px;
    |      }
    |      /* Remove the bottom border on the jumbotron for visual effect */
    |      .jumbotron {
    |        border-bottom: 0;
    |      }
    |    }
    |    </style>
    |  </head>
    |
    |  <body>
    |
    |    <div class="container">
    |      <div class="header">
    |        <ul class="nav nav-pills pull-right">
    |          <li><a href="https://github.svc.oncue.com/pages/intelmedia/funnel/">About</a></li>
    |          <li><a href="mailto:timothy.m.perrett@oncue.com">Contact</a></li>
    |        </ul>
    |        <h3 class="text-muted">Chemist Control Panel</h3>
    |      </div>
    |
    |      <div class="row marketing">
    |        <div class="col-lg-6">
    |          <h4>Metric Resources</h4>
    |          <p><a href="/keys">GET /keys</a>: Display the current snapshot of all keys registred with the monitoring instance.</p>
    |          <p><a href="/keys/prefix">GET /keys/prefix</a>: Display the current snapshot of all keys prefixed by the word 'prefix'.</p>
    |
    |          <h4>Window Resources</h4>
    |          <p><a href="/now">GET /now</a>: Current values for all metrics prefixed by 'now'.</p>
    |          <p><a href="/previous">GET /previous</a>: Current values for all metrics prefixed by 'previous'.</p>
    |          <p><a href="/sliding">GET /sliding</a>: Current values for all metrics prefixed by 'sliding'.</p>
    |        </div>
    |
    |        <div class="col-lg-6">
    |          <h4>Operations Resources</h4>
    |          <p><a href="/mirror">POST /now</a>: Dynamically mirror metrics from other funnel(s).</p>
    |          <p><a href="/halt">POST /halt</a>: Stop mirroring metrics from the given funnel URLs).</p>
    |        </div>
    |      </div>
    |
    |      <div class="footer">
    |        <p>&copy; Verizon OnCue 2014</p>
    |      </div>
    |
    |    </div>
    |  </body>
    |</html>
  """.stripMargin

}
























