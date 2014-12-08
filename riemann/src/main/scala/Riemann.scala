package oncue.svc.funnel
package riemann

import com.aphyr.riemann.client.RiemannClient
import com.aphyr.riemann.Proto.{Event => REvent}
import oncue.svc.funnel.Events.Event
import java.net.URL
import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Strategy,Task,Actor}
import scalaz.stream.{async,Process}
import Monitoring.prettyURL
import scala.collection.JavaConverters._
import scalaz.stream.async.mutable.Signal
import scalaz.stream.async.signal

object Riemann {

  sealed trait Pusher
  final case class Hold(e: REvent) extends Pusher
  final case object Flush extends Pusher

  @volatile private var store: List[REvent] = Nil

  private[riemann] def collector(
    R: RiemannClient
  )(implicit log: String => Unit): Actor[Pusher] = {
    implicit val S = Strategy.Executor(Monitoring.serverPool)
    implicit val P = Monitoring.schedulingPool
    val a = Actor.actor[Pusher] {
      case Hold(e) => store = (e :: store)
      case Flush   => {
        R.sendEvents(store.asJava)
        log(s"successfully sent batch of ${store.length}")
        store = Nil
      }
    }

    Process.awakeEvery(1.minute).evalMap {_ =>
      Task(a(Flush))
    }.run.runAsync(_ => ())

    a
  }

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
    )(implicit log: String => Unit): REvent = {

    val (name, host) = pt.key.name.split(":::") match {
      case Array(n, h) => (n, Some(h))
      case _ => (pt.key.name, None)
    }

    val t = tags(name)

    val e = c.event
             .tags(t: _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis / 1000L)
             .ttl(ttl)
             .attribute("service-revision", t.headOption.getOrElse("unknown"))

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

    // lifts the EventDSL into an REvent
    e.build()
  }

  private def liftDatapointToStream(dp: Datapoint[Any]): Process[Task, Datapoint[Any]] =
    dp.value match {
      case s: Stats => Process.emitAll(splitStats(dp.key, s))
      case _        => Process.emit(dp)
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
  )(c: RiemannClient, a: Actor[Pusher]
  )(implicit log: String => Unit
  ): Task[Unit] = {
    Monitoring.subscribe(M)(_ => true).flatMap(liftDatapointToStream
      ).zipWithIndex.evalMap { case (pt,i) =>
        Task {
          a ! Hold(toEvent(c, ttlInSeconds)(pt))
          if(i % 1000 == 0)
            a ! Flush
          else ()
        }(Monitoring.defaultPool)
      // )
    }.run
  }

  private val urlSignals = new java.util.concurrent.ConcurrentHashMap[URL, Signal[Unit]]

  /**
   * Publish the values from the given monitoring instance to riemann in batches.
   */
  def publishToRiemann[A](
    M: Monitoring,
    ttlInSeconds: Float = 20f
  )(riemannClient: RiemannClient,
    riemannName: String,
    riemannRetries: Names => Event = _ => Monitoring.defaultRetries)(
    myName: String = "Funnel Mirror"
  )(implicit log: String => Unit): Task[Unit] = {

    val actor = collector(riemannClient)

    publish(M, ttlInSeconds, riemannRetries(Names("Riemann", myName, riemannName)))(riemannClient, actor)
  }
}
