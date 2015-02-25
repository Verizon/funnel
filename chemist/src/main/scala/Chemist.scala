package funnel
package chemist

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.{BufferedWriter, IOException, OutputStream, OutputStreamWriter}
import java.net.{InetSocketAddress, URL}
import journal.Logger
import oncue.svc.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Free}
import scalaz.concurrent.Task
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

object Chemist {
  private val log = Logger[Chemist.type]

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  val defaultPool: ExecutorService =
    Executors.newFixedThreadPool(4, daemonThreads("chemist-thread"))

  val serverPool: ExecutorService =
    Executors.newCachedThreadPool(daemonThreads("chemist-server"))

  val schedulingPool: ScheduledExecutorService =
    Executors.newScheduledThreadPool(2, daemonThreads("chemist-scheduled-tasks"))

}

class Chemist


// class Chemist(I: Interpreter[Server.ServerF], port: Int){
//   import Chemist.log
//   import argonaut._, Argonaut._, JSON._

//   private val S = Server
//   private val server = HttpServer.create(new InetSocketAddress(port), 0)

//   def start(): Unit = {
//     server.setExecutor(Server.serverPool)
//     val _ = server.createContext("/", handler)
//     server.start()
//   }

//   def stop(): Unit = server.stop(0)

//   private def run[A : EncodeJson](
//     exe: Free[Server.ServerF, A],
//     req: HttpExchange
//   ): Unit = {
//     I.run(exe).attemptRun match {
//       case \/-(a) => flush(200, a.asJson.nospaces, req)
//       case -\/(e) => flush(500, e.toString, req)
//     }
//   }

//   private def flush(status: Int, body: String, req: HttpExchange): Unit = {
//     val bytes = body.getBytes
//     req.sendResponseHeaders(status,bytes.length)
//     req.getResponseBody.write(bytes)
//   }

//   protected def handleIndex(req: HttpExchange): Unit = {
//     req.sendResponseHeaders(200, indexHTML.length)
//     req.getResponseBody.write(indexHTML.getBytes)
//   }

//   // Tim: this shouldnt be implemented, right? allocations of work to shards
//   // should happen automatically. Unless we want some override / manual input?
//   // protected def handleDistribute(req: HttpExchange): Unit = {
//   //   req.sendResponseHeaders(200,0)
//   //   req.getResponseBody.write("Nothing to see here yet.".getBytes)
//   // }

//   protected def handleBootstrap(req: HttpExchange): Unit = {
//     if(req.getRequestMethod.toLowerCase == "post"){
//       run(S.bootstrap, req)
//       flush(200, "", req)
//     } else flush(400, "Method not allowed. Use POST.",req)
//   }

//   protected def handleAlterShardState[A : EncodeJson](f: Free[Server.ServerF, A])(req: HttpExchange): Unit = {
//     if(req.getRequestMethod.toLowerCase == "post"){
//       run(f, req)
//       flush(201, "", req)
//     } else flush(400, "Method not allowed. Use POST.",req)
//   }

//   protected def handleNotImplemented(req: HttpExchange): Unit = {
//     req.sendResponseHeaders(501,0)
//   }

//   protected def handleStatus(req: HttpExchange): Unit =
//     req.sendResponseHeaders(200,0)

//   protected def handler = new HttpHandler {
//     def handle(req: HttpExchange): Unit = try {
//       val path = req.getRequestURI.getPath match {
//         case "/" => Nil
//         case p   => p.split("/").toList.tail
//       }

//       path match {
//         // GET
//         case Nil                            => handleIndex(req)
//         case "status"                :: Nil => handleStatus(req)
//         case "distribution"          :: Nil => run(S.distribution.map(_.toList), req)
//         case "shards"                :: Nil => run(S.shards.map(_.toList), req)
//         case "shards" :: id          :: Nil => run(S.shard(id), req)
//         case "lifecycle" :: "history" :: Nil => run(S.history.map(_.toList), req)
//         // POST
//         case "shards" :: id :: "exclude" :: Nil => handleAlterShardState(S.exclude(id))(req)
//         case "shards" :: id :: "include" :: Nil => handleAlterShardState(S.include(id))(req)
//         case "distribute"   :: Nil              => handleNotImplemented(req)
//         case "bootstrap"    :: Nil              => handleBootstrap(req)
//         case _                                  => handleNotImplemented(req)
//       }
//     }
//     catch {
//       case e: Exception => log.error("fatal error: " + e)
//     }
//     finally req.close
//   }

//   val indexHTML = s"""
//     |<!DOCTYPE html>
//     |<html lang="en">
//     |  <head>
//     |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css">
//     |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css">
//     |    <script src="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"></script>
//     |    <title>Chemist &middot; ${BuildInfo.version} &middot; ${BuildInfo.gitRevision}</title>
//     |    <style type="text/css">
//     |    /* Space out content a bit */
//     |    body {
//     |    padding-top: 20px;
//     |    padding-bottom: 20px;
//     |    }
//     |
//     |    /* Everything but the jumbotron gets side spacing for mobile first views */
//     |    .header,
//     |    .marketing,
//     |    .footer {
//     |    padding-right: 15px;
//     |    padding-left: 15px;
//     |    }
//     |
//     |    /* Custom page header */
//     |    .header {
//     |    border-bottom: 1px solid #e5e5e5;
//     |    }
//     |    /* Make the masthead heading the same height as the navigation */
//     |    .header h3 {
//     |    padding-bottom: 19px;
//     |    margin-top: 0;
//     |    margin-bottom: 0;
//     |    line-height: 40px;
//     |    }
//     |
//     |    /* Custom page footer */
//     |    .footer {
//     |    padding-top: 19px;
//     |    color: #777;
//     |    border-top: 1px solid #e5e5e5;
//     |    }
//     |
//     |    /* Customize container */
//     |    @media (min-width: 768px) {
//     |    .container {
//     |    max-width: 730px;
//     |    }
//     |    }
//     |    /* Supporting marketing content */
//     |    .marketing {
//     |    margin: 40px 0;
//     |    }
//     |    .marketing p + h4 {
//     |    margin-top: 28px;
//     |    }
//     |
//     |    /* Responsive: Portrait tablets and up */
//     |    @media screen and (min-width: 768px) {
//     |    /* Remove the padding we set earlier */
//     |    .header,
//     |    .marketing,
//     |    .footer {
//     |    padding-right: 0;
//     |    padding-left: 0;
//     |    }
//     |    /* Space out the masthead */
//     |    .header {
//     |    margin-bottom: 30px;
//     |    }
//     |    }
//     |    </style>
//     |  </head>
//     |
//     |  <body>
//     |    <div class="container">
//     |      <div class="header">
//     |        <ul class="nav nav-pills pull-right">
//     |          <li><a href="https://github.svc.oncue.com/pages/intelmedia/funnel/">About</a></li>
//     |          <li><a href="mailto:timothy.m.perrett@oncue.com">Contact</a></li>
//     |        </ul>
//     |        <h3 class="text-muted">Chemist Control Panel</h3>
//     |      </div>
//     |
//     |      <div class="row marketing">
//     |
//     |        <div class="col-lg-6">
//     |          <h4>Distribution Resources</h4>
//     |          <p><a href="/distribution">GET /distribution</a>: Display the current distribution of shards and associated work.</p>
//     |          <p><a href="/lifecycle/history">GET /lifecycle/history</a>: View a rolling snapshot of the last lifecycle events to took place.</p>
//     |          <p><a href="/distribute">POST /distribute</a>: Manually instruct a given set of inputs to be sharded over avalible Flask instances.</p>
//     |          <p><a href="/bootstrap">POST /bootstrap</a>: Manually force Chemist to re-read its state of the world from AWS.</p>
//     |        </div>
//     |
//     |        <div class="col-lg-6">
//     |          <h4>Shard Resources</h4>
//     |          <p><a href="/shards">GET /shards</a>: List all shards and known information about those hosts</p>
//     |          <p><a href="/shard/:shardid">GET /shard/:shard-id</a>: Displays all known information about a given shard.</p>
//     |          <p><a href="/shard/:shardid/include">POST /shard/:shard-id/include</a>: Manually specify a machine to use as a load-bearing shard.</p>
//     |          <p><a href="/shard/:shardid/exclude">POST /shard/:shard-id/exclude</a>: Manually remove a shard from rotation and reshard its work.</p>
//     |        </div>
//     |
//     |      </div>
//     |
//     |      <div class="footer">
//     |        <p>&copy; Verizon OnCue 2014</p>
//     |      </div>
//     |
//     |    </div>
//     |  </body>
//     |</html>
//   """.stripMargin

// }

