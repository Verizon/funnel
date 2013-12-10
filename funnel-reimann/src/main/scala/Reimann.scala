package intelmedia.ws.monitoring
package reimann

import com.aphyr.riemann.client.RiemannClient
import intelmedia.ws.monitoring.Events.Event
import java.net.URL
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.{async,Process}
import scalaz.stream.async.immutable.Signal
import Monitoring.prettyURL

object Riemann {

  def top(s: String) = s.split("/").headOption.getOrElse(s)
  def tags(s: String) = s.split("/")

  private def toEvent(c: RiemannClient, ttl: Float)(
                      pt: Datapoint[Any])(
                      implicit log: String => Unit =
                        s => "[Riemann.toEvent] "+s): Task[Unit] = Task {
    val e = c.event.service(top(pt.key.name))
             .tags(tags(pt.key.name): _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis)
             .ttl(ttl)
    val e2 = pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(a)
      case b: Boolean => e.state(b.toString)
      case _ => ???
    }
    log("sending: " + e2)
    e2.send()
    log("successfully sent " + e2)
  } (Monitoring.defaultPool)

  /**
   * Try running the process `p`, retrying in the event of failure.
   * Example: `retry(Process.awakeEvery(2 minutes))(p)` will wait
   * 2 minutes after each failure before trying again, indefinitely.
   * Using `retry(Process.awakeEvery(2 minutes).take(5))(p)` will do
   * the same, but only retry a total of five times before raising
   * the latest error.
   */
  def retry[A](retries: Process[Task,Any])(p: Process[Task,A]): Process[Task,A] =
    p.attempt().stripW ++
    retries.terminated.flatMap {
      // on our last reconnect attempt, rethrow error
      case None => p.attempt().flatMap(_.fold(Process.fail, Process.emit))
      // on other attempts, ignore the exceptions
      case Some(_) => p.attempt().stripW
    }

  /** Terminate `p` when the given `Signal` terminates. */
  def link[A](alive: Signal[Unit])(p: Process[Task,A]): Process[Task,A] =
    alive.continuous.zip(p).map(_._2)

  /**
   * Publish all datapoints from this `Monitoring` to the given
   * `RiemannClient`. Returns a thunk that can be used to terminate.
   * Uses `retries` to control the reconnect frequency if `RiemannClient`
   * is unavailable.
   */
  def publish(M: Monitoring, ttlInSeconds: Float, retries: Event)(c: RiemannClient)(
             implicit log: String => Unit = s => println("[Riemann.publish] "+s)):
             () => Unit = {
    val alive = async.signal[Unit](Strategy.Executor(Monitoring.defaultPool))
    alive.value.set(())
    link(alive) { Monitoring.subscribe(M)(_ => true).flatMap { pt =>
      retry(retries(M))(Process.eval_(toEvent(c, ttlInSeconds)(pt)))
    }}.run.runAsync(_.fold(err => log(err.toString), _ => ()))
    () => alive.value.close
  }

  /**
   * Mirror all datapoints from the given nodes into `M`, then publish
   * these datapoints to Riemann. `nodeRetries` controls how often we
   * attempt to reconnect to a node that we've failed to connect to, and
   * `reimannRetries` controls how often we attempt to reconnect to the
   * Riemann server in the event of an error.
   */
  def mirrorAndPublish[A](M: Monitoring, ttlInSeconds: Float, nodeRetries: Event)(
                          c: RiemannClient, reimannRetries: Event)(
                          parse: DatapointParser)(
                          groupedUrls: Process[Task, (URL,String)])(
                          implicit log: String => Unit =
                            s => println("[Riemann.publishAll] "+s)):
                          () => Unit = {
    val alive = async.signal[Unit](Strategy.Executor(Monitoring.defaultPool))
    alive.value.set(())
    // use urls as the local names for keys
    var seenURLs = Set[(URL,String)]()
    link(alive)(groupedUrls).evalMap { case (url,group) => Task.delay {
      val localName = prettyURL(url)
      link(alive)(M.attemptMirrorAll(parse)(nodeRetries)(
        url, m => s"$group/$m/$localName"
      )).run.runAsync(_ => ())
    }}.run.runAsync(_ => ())
    publish(M, ttlInSeconds, reimannRetries)(c)
    () => alive.close.run
  }
}
