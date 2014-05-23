package intelmedia.ws.funnel
package riemann

import com.aphyr.riemann.client.RiemannClient
import intelmedia.ws.funnel.Events.Event
import java.net.URL
import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.{async,Process}
import scalaz.stream.async.immutable.Signal
import Monitoring.prettyURL

object Riemann {

  private def splitStats(key: Key[Any], s: Stats): List[Datapoint[Any]] = {
    val (k, tl) = key.name.split(":::") match {
      case Array(hd,tl) => (key.rename(hd), tl)
      case _ => (key,"")
    }
    val tl2 = if (tl.isEmpty) "" else ":::"+tl
    List(
      Datapoint(k.modifyName(_ + "/count" + tl2), s.count.toDouble),
      Datapoint(k.modifyName(_ + "/variance" + tl2), s.variance),
      Datapoint(k.modifyName(_ + "/mean" + tl2), s.mean),
      Datapoint(k.modifyName(_ + "/last" + tl2), s.last.getOrElse(Double.NaN)),
      Datapoint(k.modifyName(_ + "/standardDeviation" + tl2), s.standardDeviation)
    )
  }

  private def trafficLightToRiemannState(dp: Datapoint[Any]): Datapoint[Any] =
    if(dp.units == Units.TrafficLight)
      dp.value match {
        case TrafficLight.Red     => Datapoint(dp.key, "critical")
        case TrafficLight.Yellow  => Datapoint(dp.key, "warning")
        case TrafficLight.Green   => Datapoint(dp.key, "ok")
      }
    else dp

  private def tags(s: String) = s.split("/")

  /**
    * This whole method is an imperitive getho. However, we use the
    * Riemann java client DSL, and make "use" of the fact that the underlying
    * objects are actually affecting the protocol buffers wire format.
    *
    * This method really needs to die, but unless we go to the trouble of
    * implementing our own client, that's not going to happen as there has to
    * be some plumbing to mediate from our world to the riemann world.
    *
    * C'est la vie!
    */
  private def toEvent(c: RiemannClient, ttl: Float)(pt: Datapoint[Any])(
                      implicit log: String => SafeUnit): SafeUnit = {

    val (name, host) = pt.key.name.split(":::") match {
      case Array(n, h) => (n, Some(h))
      case _ => (pt.key.name, None)
    }

    val e = c.event
             .tags(tags(name): _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis / 1000L)
             .ttl(ttl)

    e.service(name)
    host.foreach { h =>
      e.host(h)
    }

    pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(trafficLightToRiemannState(pt).value.toString)
      case b: Boolean => e.state(b.toString)
      // will *never* be encountered at this point
      // case s: Stats =>
      case x => log("]]]]]]]]]]]]]]] "+x.getClass.getName); ???
    }

    val logPoint = pt.copy(key = pt.key.copy(name = name))

    log("sending: " + logPoint)

    try e.send()
    catch { case err: Exception =>
      log("unable to send datapoint to Reimann server due to: " + e)
      log("waiting")
      throw err
    }
    log("successfully sent " + logPoint)
  }

  /**
   * Try running the process `p`, retrying in the event of failure.
   * Example: `retry(Process.awakeEvery(2 minutes))(p)` will wait
   * 2 minutes after each failure before trying again, indefinitely.
   * Using `retry(Process.awakeEvery(2 minutes).take(5))(p)` will do
   * the same, but only retry a total of five times before raising
   * the latest error.
   */
  def retry[A](retries: Process[Task,Any])(p: Process[Task,A]): Process[Task,A] = {
    val alive = async.signal[SafeUnit]; alive.value.set(())
    val step: Process[Task,Throwable \/ A] =
      p.append(Process.eval_(alive.close)).attempt()
    step.flatMap(_.fold(_ => link(alive)(retries).terminated.flatMap {
      // on our last reconnect attempt, rethrow error
      case None => step.flatMap(_.fold(Process.fail, Process.emit))
      // on other attempts, ignore the exceptions
      case Some(_) => step.stripW
    }, Process.emit))
  }

  /** Terminate `p` when the given `Signal` terminates. */
  def link[A](alive: Signal[SafeUnit])(p: Process[Task,A]): Process[Task,A] =
    alive.continuous.zip(p).map(_._2)

  private def liftDatapointToStream(dp: Datapoint[Any]): Process[Task, Datapoint[Any]] =
    dp.value match {
      case s: Stats => Process.emitSeq(splitStats(dp.key, s))
      case _        => Process.emit(dp)
    }

  /**
   * Publish all datapoints from this `Monitoring` to the given
   * `RiemannClient`. Uses `retries` to control the reconnect frequency
   * if `RiemannClient` is unavailable. This returns a blocking `Task`.
   */
  def publish(M: Monitoring, ttlInSeconds: Float = 20f,
             retries: Event = Events.every(1 minutes))(c: RiemannClient)(
             implicit log: String => SafeUnit
  ): Task[SafeUnit] = {
    for {
      alive <- Task(async.signal[SafeUnit](Strategy.Executor(Monitoring.defaultPool)))
      _     <- alive.set(())
      _     <- link(alive){
                 Monitoring.subscribe(M)(_ => true).flatMap(liftDatapointToStream).flatMap { pt =>
                  retry(retries(M))(Process.eval_(
                    Task {
                      toEvent(c, ttlInSeconds)(pt)
                    }(Monitoring.defaultPool))
                  )
                }
               }.run
      _     <- alive.close
    } yield ()
  }

  def defaultRetries = Events.takeEvery(30 seconds, 6)

  case class Names(kind: String, mine: String, theirs: String)

  /**
   * Mirror all datapoints from the given nodes into `M`, then publish
   * these datapoints to Riemann. `nodeRetries` controls how often we
   * attempt to reconnect to a node that we've failed to connect to, and
   * `reimannRetries` controls how often we attempt to reconnect to the
   * Riemann server in the event of an error.
   */
  def mirrorAndPublish[A](
      M: Monitoring, ttlInSeconds: Float = 20f, nodeRetries: Names => Event = _ => defaultRetries)(
      c: RiemannClient,
      riemannName: String,
      riemannRetries: Names => Event = _ => defaultRetries)(
      parse: DatapointParser)(
      groupedUrls: Process[Task, (URL,String)], myName: String = "Funnel Mirror")(
      implicit log: String => SafeUnit): Task[SafeUnit] = {

    val S = Strategy.Executor(Monitoring.defaultPool)
    val alive = async.signal[SafeUnit](S)
    val active = async.signal[Set[URL]](S); active.value.set(Set())
    def modifyActive(f: Set[URL] => Set[URL]): Task[SafeUnit] =
      active.compareAndSet(a => Some(f(a.getOrElse(Set())))).map(_ => ())
    for {
      _ <- alive.set(())
      _ <- link(alive)(groupedUrls).evalMap { case (url,group) =>
            Task.delay {
               // adding the `localName` onto the string here so that later in the
               // process its possible to find the key we're specifically looking for
               // and trim off the `localName`
               val localName = prettyURL(url)
               val received = link(alive) {
                 M.attemptMirrorAll(parse)(nodeRetries(Names("Funnel", myName, localName)))(
                   url, m => s"$group/$m:::$localName")
               }
               val receivedIdempotent = Process.eval(active.get).flatMap { urls =>
                 if (urls.contains(url)) Process.halt // skip it, alread running
                 else Process.eval_(modifyActive(_ + url)) ++ // add to active at start
                      received.onComplete(Process.eval_(modifyActive(_ - url))) // and remove it when done
               }
               receivedIdempotent.run.runAsync(_.fold(err => log(err.getMessage), identity))
             }
           }.merge(Process.eval(publish(M, ttlInSeconds,
             riemannRetries(Names("Riemann", myName, riemannName)))(c))).run
             // publish to Riemann concurrent to aggregating locally
      _ <- alive.close
    } yield ()
  }
}
