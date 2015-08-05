package funnel
package chemist

import scalaz.\/
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process, time}
import scalaz.std.string._
import scalaz.std.set._
import scalaz.syntax.apply._
import scalaz.syntax.kleisli._
import scalaz.syntax.traverse._
import scalaz.syntax.id._
import scalaz.std.vector._
import scalaz.std.option._
import journal.Logger
import java.net.{ InetSocketAddress, Socket, URI, URL }
import TargetLifecycle._

object Housekeeping {
  import Sharding._
  import concurrent.duration._

  private lazy val log = Logger[Housekeeping.type]
  private lazy val defaultPool = Strategy.Executor(Chemist.defaultPool)
  private def debugString(a: Any): Unit = log.debug(a.toString)

  /**
    * Try running the given process `p`, catching errors and reporting
    * them with `maskedError`, using `schedule` to determine when further
    * attempts are made. If `schedule` is exhausted, the error is raised.
    * Example: `attemptRepeatedly(println)(p)(Process.awakeEvery(10 seconds).take(3))`
    * will run `p`; if it encounters an error, it will print the error using `println`,
    * then wait 10 seconds and try again. After 3 reattempts it will give up and raise
    * the error in the `Process`.
   */
  private[chemist] def attemptRepeatedly[A](
    maskedError: Throwable => Unit)(
    p: Process[Task,A])(
    schedule: Process[Task,Unit]): Process[Task,A] = {
    val step: Process[Task, Throwable \/ A] =
      p.append(schedule.kill).attempt(e => Process.eval { Task.delay { maskedError(e); e }})
    val retries: Process[Task, Throwable \/ A] = schedule.zip(step.repeat).map(_._2)
    (step ++ retries).last.flatMap(_.fold(Process.fail, Process.emit))
  }

  private[chemist] def contact(uri: URI): Throwable \/ Unit =
    \/.fromTryCatchThrowable[Unit, Exception]{
      val s = new Socket
      // timeout in 300ms to keep the overhead reasonable
      try s.connect(new InetSocketAddress(uri.getHost, uri.getPort), 300)
      finally s.close // whatever the outcome, close the socket to prevent leaks.
    }

  private[chemist] val iSchedule: Process[Task, Unit] = Process.iterate(1)(_ * 2).take(6).flatMap(t => time.sleep(t.hours)(defaultPool, Chemist.schedulingPool) ++ Process.emit(()))

  /**
   * This is a collection of the tasks that are needed to run on a periodic basis.
   * Specifically this involves the following:
   * 1. ensuring that we pickup any target instances that we were not already moniotring
   * 2. ensuring that targets that are stuck in the assigned state for more than a given 
   *    period are reaped, and re-assigned to another flask (NOT IMPLEMENTED.)
   */
  def periodic(delay: Duration)(discovery: Discovery, repository: Repository): Process[Task,Unit] = {
    def fail(t: Target) = 
      Process.eval(
        Task.delay(
          repository.platformHandler(PlatformEvent.TerminatedTarget(t.uri))
        )
      )

    def succeed(t: Target) =
      fail(t) ++
      Process.eval(
        Task.delay(
          repository.platformHandler(PlatformEvent.NewTarget(t))
        )
      )

    def reachOut(t: Target) = Process.eval(Task.fromDisjunction(contact(t.uri))) ++ succeed(t)

    time.awakeEvery(delay)(defaultPool, Chemist.schedulingPool).evalMap(_ =>
      for {
        _ <- gatherUnassignedTargets(discovery, repository)

        ts <- repository.states.map(_.apply(TargetState.Investigating).values.map(_.msg.target))
        _   = for {
          t  <- ts
          _   = attemptRepeatedly(debugString)(reachOut(t))(iSchedule).onFailure(e => fail(t)).run
        } yield ()
      } yield ()
    )

  }

  /**
   * Gather up all the targets that are for reasons unknown sitting in the unassigned
   * target state. We'll read the list of the whole world, and delta that against the known
   * distribution, and assign any outliers. This is a fall back mechinism.
   */
  def gatherUnassignedTargets(discovery: Discovery, repository: Repository): Task[Unit] =
    for {
      // read the state of the existing world
      d <- repository.distribution

      // read the list of all deployed machines
      l <- discovery.listTargets
      _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")

      // // figure out given the existing distribution, and the differencen between what's been discovered
      // // STU: the fact that I'm throwing ID away here is suspect
      unmonitored = l.foldLeft(Set.empty[Target])(_ ++ _._2) &~ d.values.foldLeft(Set.empty[Target])(_ ++ _)
      _  = log.info(s"located instances: monitorable=${l.size}, unmonitored=${unmonitored.size}")

      // // action the new targets by putting them in the monitoring lifecycle
      targets = unmonitored.map(t => PlatformEvent.NewTarget(t))
      _ <- targets.toVector.traverse_(repository.platformHandler)
      _  = log.info(s"added ${targets.size} targets to the repository...")
    } yield ()

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * This function should only really be used startup of chemist.
   */
  def gatherAssignedTargets(flasks: Seq[Flask])(http: dispatch.Http): Task[Distribution] = {
    val d = (for {
       a <- Task.gatherUnordered(flasks.map(
         f => requestAssignedTargets(f.location)(http).map(f -> _).flatMap { t =>
           Task.delay {
             log.debug(s"Read targets $t from flask $f")
             t
           }
         }
       ))
    } yield a.foldLeft(Distribution.empty){ (a,b) =>
      a.updateAppend(b._1.id, b._2)
    }).map { dis =>
      log.debug(s"Gathered distribution $dis")
      dis
    }
    d or Task.now(Distribution.empty)
  }

  import funnel.http.{Cluster,JSON => HJSON}

  /**
   * Call out to the specific location and grab the list of things the flask
   * is already mirroring.
   */
  private def requestAssignedTargets(location: Location)(http: dispatch.Http): Task[Set[Target]] = {
    import argonaut._, Argonaut._, JSON._, HJSON._
    import dispatch._, Defaults._

    val a = location.uriFromTemplate(LocationTemplate(s"http://@host:@port/mirror/sources"))
    val req = Task.delay(url(a.toString)) <* Task.delay(log.debug(s"requesting assigned targets from $a"))
    req flatMap { b =>
      http(b OK as.String).map { c =>
        Parse.decodeOption[List[Cluster]](c
        ).toList.flatMap(identity
        ).map { cluster =>
          log.debug(s"Received cluster $cluster from $a")
          cluster
        }.foldLeft(Set.empty[Target]){ (a,b) =>
          b.urls.map(s => Target(b.label, new URI(s), false)).toSet
        }
      }
    }
  }
}
