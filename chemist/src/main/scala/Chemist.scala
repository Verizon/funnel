package funnel
package chemist

import knobs.{loadImmutable,Required,FileResource,ClassPathResource}
import java.io.File
import journal.Logger
import oncue.svc.funnel.BuildInfo
import scalaz.{\/,-\/,\/-,Kleisli}
import scalaz.syntax.kleisli._
import scalaz.concurrent.Task
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

object Chemist {
  import Sharding.Target

  private val log = Logger[Chemist.type]

  //////////////////////// PUBLIC API ////////////////////////////

  def main(args: Array[String]): Unit = {
    (for {
      a <- knobs.loadImmutable(Required(
        FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
        ClassPathResource("oncue/chemist.cfg")) :: Nil)
      b <- knobs.aws.config
      _ <- exe.run(Config.readConfig(a ++ b))
    } yield ()).run
  }

  //////////////////////// SERVICE API ////////////////////////////

  val exe: Chemist[Unit] = for {
    _ <- init()
  } yield ()

  def bootstrap: Chemist[Unit] = for {
    cfg <- config
    // read the list of all deployed machines
    l <- Deployed.list(cfg.asg, cfg.ec2).liftKleisli
    _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

    // filter out all the instances that are in private networks
    // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
    z  = l.filterNot(_.location.isPrivateNetwork)
          .filterNot(Deployed.isFlask)
    _  = log.info(s"located ${z.length} instances that appear to be monitorable")

    // convert the instance list into reachable targets
    t  = z.flatMap(Target.fromInstance(cfg.resources)).toSet
    _  = log.debug(s"targets are: $t")

    // set the result to an in-memory list of "the world"
    _ <- Task.gatherUnordered(z.map(cfg.repository.addInstance)).liftKleisli
    _  = log.info("added instances to the repository...")

    // from the whole world, figure out which are flask instances
    f  = l.filter(Deployed.isFlask)
    _  = log.info(s"found ${f.length} flasks in the running instance list...")

    // update the distribution with new capacity seeds
    _ <- Task.gatherUnordered(f.map(cfg.repository.increaseCapacity)).liftKleisli
    _  = log.debug("increased the known monitoring capactiy based on discovered flasks")

    // ask those flasks for their current work and yield a `Distribution`
    d <- Sharding.gatherAssignedTargets(f).liftKleisli
    _  = log.debug("read the existing state of assigned work from the remote instances")

    // update the distribution accordingly
    _ <- cfg.repository.mergeDistribution(d).liftKleisli
    _  = log.debug("merged the currently assigned work into the current distribution")

    _ <- (for {
      h <- Sharding.locateAndAssignDistribution(t, cfg.repository)
      g <- Sharding.distribute(h)
    } yield ()).liftKleisli

    _ <- Task.now(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<")).liftKleisli
  } yield ()


  protected def init(): Chemist[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config

      // start to wire up the topics and subscriptions to queues
      a <- SNS.create(cfg.queue.topicName)(cfg.sns).liftKleisli
      _  = log.debug(s"created sns topic with arn = $a")

      b <- SQS.create(cfg.queue.topicName, a)(cfg.sqs).liftKleisli
      _  = log.debug(s"created sqs queue with arn = $b")

      c <- SNS.subscribe(a, b)(cfg.sns).liftKleisli
      _  = log.debug(s"subscribed sqs queue to the sns topic")

      // now the queues are setup with the right permissions,
      // start the lifecycle listener
      _ <- Lifecycle.run(cfg.queue.topicName, cfg.resources, Lifecycle.sink
            )(cfg.repository, cfg.sqs, cfg.asg, cfg.ec2).liftKleisli
      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }

  //////////////////////// INTERNALS ////////////////////////////

  private val config: Chemist[ChemistConfig] = Kleisli.ask[Task, ChemistConfig]

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



// }

