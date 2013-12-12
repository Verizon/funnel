package intelmedia.ws.monitoring
package riemann

import com.aphyr.riemann.client.RiemannClient
import intelmedia.ws.monitoring.Events.Event
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
                      implicit log: String => Unit =
                        s => "[Riemann.toEvent] "+s): Unit = {

    val e = c.event.service(pt.key.name)
             .tags(tags(pt.key.name): _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis / 1000L)
             .ttl(ttl)

    // bawws like side-effects as the underlying api is totally mutable.
    // GO JAVA!!
    pt.key.name.split(":::") match {
      case Array(name,host) => e.host(host)
      case _                => ()
    }

    pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(trafficLightToRiemannState(pt).value.toString)
      case b: Boolean => e.state(b.toString)
      // will *never* be encountered at this point
      // case s: Stats => 
      case x => log("]]]]]]]]]]]]]]] "+x.getClass.getName); ???
    }

    log("sending: " + pt)
    
    try e.send()
    catch { case err: Exception =>
      log("unable to send datapoint to Reimann server due to: " + e)
      log("waiting")
      throw err
    }
    log("successfully sent " + pt)
  }

  /**
   * Try running the process `p`, retrying in the event of failure.
   * Example: `retry(Process.awakeEvery(2 minutes))(p)` will wait
   * 2 minutes after each failure before trying again, indefinitely.
   * Using `retry(Process.awakeEvery(2 minutes).take(5))(p)` will do
   * the same, but only retry a total of five times before raising
   * the latest error.
   */
  def retry[A](retries: Process[Task,Any])(p: Process[Task,A]):
  Process[Task,A] = {
    val alive = async.signal[Unit]; alive.value.set(())
    val step: Process[Task,Throwable \/ A] =
      p.append(Process.eval_(alive.close)).attempt()
    step.stripW ++ link(alive)(retries).terminated.flatMap {
      // on our last reconnect attempt, rethrow error
      case None => step.flatMap(_.fold(Process.fail, Process.emit))
      // on other attempts, ignore the exceptions
      case Some(_) => step.stripW
    }
  }

  /** Terminate `p` when the given `Signal` terminates. */
  def link[A](alive: Signal[Unit])(p: Process[Task,A]): Process[Task,A] =
    alive.continuous.zip(p).map(_._2)

  private def liftDatapointToStream(dp: Datapoint[Any]): Process[Task, Datapoint[Any]] = 
    dp.value match {
      case s: Stats => Process.emitSeq(splitStats(dp.key, s))
      case _        => Process.emit(dp)
    }

  /**
   * Publish all datapoints from this `Monitoring` to the given
   * `RiemannClient`. Returns a thunk that can be used to terminate.
   * Uses `retries` to control the reconnect frequency if `RiemannClient`
   * is unavailable.
   */
  def publish(M: Monitoring, ttlInSeconds: Float = 20f,
             retries: Event = Events.every(1 minutes))(c: RiemannClient)(
             implicit log: String => Unit = s => println("[Riemann.publish] "+s)):
             () => Unit = {
    val alive = async.signal[Unit](Strategy.Executor(Monitoring.defaultPool))
    alive.value.set(())
    link(alive){ 
      Monitoring.subscribe(M)(_ => true).flatMap(liftDatapointToStream).flatMap { pt =>
        retry(retries(M))(Process.eval_(
          Task { 
            toEvent(c, ttlInSeconds)(pt) 
          }(Monitoring.defaultPool))
        )
      }
    }.run.runAsync(_.fold(err => log(err.toString), _ => ()))
    () => alive.value.close
  }

  /**
   * Mirror all datapoints from the given nodes into `M`, then publish
   * these datapoints to Riemann. `nodeRetries` controls how often we
   * attempt to reconnect to a node that we've failed to connect to, and
   * `reimannRetries` controls how often we attempt to reconnect to the
   * Riemann server in the event of an error.
   */
  def mirrorAndPublish[A](
      M: Monitoring, ttlInSeconds: Float = 20f, nodeRetries: Event = Events.every(1 minutes))(
      c: RiemannClient, reimannRetries: Event = Events.every(1 minutes))(
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
        url, m => s"$group/$m:::localName"
      )).run.runAsync(_ => ())
    }}.run.runAsync(_ => ())
    publish(M, ttlInSeconds, reimannRetries)(c)
    () => alive.close.run
  }
}
