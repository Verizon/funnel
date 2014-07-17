package intelmedia.ws.funnel
package riemann

import com.aphyr.riemann.client.RiemannClient
import intelmedia.ws.funnel.Events.Event
import java.net.URL
import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.{async,Process}
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
  private def toEvent(c: RiemannClient, ttl: Float)(pt: Datapoint[Any]
    )(implicit log: String => Unit): Unit = {

    val (name, host) = pt.key.name.split(":::") match {
      case Array(n, h) => (n, Some(h))
      case _ => (pt.key.name, None)
    }

    val e = c.event
             .tags(tags(name): _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis / 1000L)
             .ttl(ttl)

    val _1 = e.service(name)
    host.foreach { h =>
      e.host(h)
    }

    val _2 = pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(trafficLightToRiemannState(pt).value.toString)
      case b: Boolean => e.state(b.toString)
      // will *never* be encountered at this point
      // case s: Stats =>
      case x => log("]]]]]]]]]]]]]]] "+x.getClass.getName); ???
    }

    val logPoint = pt.copy(key = pt.key.copy(name = name))

    try e.send()
    catch { case err: Exception =>
      log("unable to send datapoint to Reimann server due to: " + e)
      log("waiting")
      throw err
    }
    log("successfully sent " + logPoint)
  }

  private def liftDatapointToStream(dp: Datapoint[Any]): Process[Task, Datapoint[Any]] =
    dp.value match {
      case s: Stats => Process.emitSeq(splitStats(dp.key, s))
      case _        => Process.emit(dp)
    }

  /** Terminate `p` when the given `Signal` terminates. */
  def link[A](alive: SampledSignal[Unit])(p: Process[Task,A]): Process[Task,A] =
    alive.continuous.zip(p).map(_._2)

  /**
   * Try running the process `p`, retrying in the event of failure.
   * Example: `retry(Process.awakeEvery(2 minutes))(p)` will wait
   * 2 minutes after each failure before trying again, indefinitely.
   * Using `retry(Process.awakeEvery(2 minutes).take(5))(p)` will do
   * the same, but only retry a total of five times before raising
   * the latest error.
   */
  def retry[A](retries: Process[Task,Any])(p: Process[Task,A]): Process[Task,A] = {
    val alive = SampledSignal[Unit]
    Process.eval_(alive.set(())) ++ {
      val step: Process[Task,Throwable \/ A] =
        p.append(Process.eval_(alive.close)).attempt()

      step.flatMap(_.fold(_ => link(alive)(retries).terminated.flatMap {
        // on our last reconnect attempt, rethrow error
        case None => step.flatMap(_.fold(Process.fail, Process.emit))
        // on other attempts, ignore the exceptions
        case Some(_) => step.stripW
      }, Process.emit))
    }
  }

  /**
   * Publish all datapoints from this `Monitoring` to the given
   * `RiemannClient`. Uses `retries` to control the reconnect frequency
   * if `RiemannClient` is unavailable. This returns a blocking `Task`.
   */
  def publish(
    M: Monitoring,
    ttlInSeconds: Float = 20f,
    retries: Event = Events.every(1 minutes)
  )(c: RiemannClient
  )(implicit log: String => Unit
  ): Task[Unit] = {
    Monitoring.subscribe(M)(_ => true).flatMap(liftDatapointToStream).evalMap { pt =>
      // retry(retries(M))(Process.eval_(
        Task {
          toEvent(c, ttlInSeconds)(pt)
        }(Monitoring.defaultPool)
      // )
    }.run
  }

  def defaultRetries = Events.takeEvery(30 seconds, 6)

  case class Names(kind: String, mine: String, theirs: String)

  def mirrorAndPublish[A](
    M: Monitoring,
    ttlInSeconds: Float = 20f,
    nodeRetries: Names => Event = _ => defaultRetries
  )(c: RiemannClient,
    riemannName: String,
    riemannRetries: Names => Event = _ => defaultRetries)(
    parse: DatapointParser)(
    groupedUrls: Process[Task, (URL,String)],
    myName: String = "Funnel Mirror"
  )(implicit log: String => Unit): Task[Unit] = {

    groupedUrls.evalMap { case (url,group) =>
      Task.delay {
        M.mirrorAll(parse)(url, _ + url.toString.replaceAll("/", "_")
          ).run.attempt.runAsync(_.fold(println, println))
        println("Recieved URL: " + group + "/" + url)
      }
    }.run.runAsync(_ => ())

    // M.distinctKeys.zipWithIndex.evalMap(x => Task(println("~~~~ " + x))).run.runAsync(_ => ())

    publish(M, ttlInSeconds, riemannRetries(Names("Riemann", myName, riemannName)))(c)

    // Monitoring.subscribe(M)(_ => true).zipWithIndex.evalMap(
    //    )).run

  }
}
