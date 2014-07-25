package intelmedia.ws.funnel
package riemann

import com.aphyr.riemann.client.RiemannClient
import com.aphyr.riemann.Proto.{Event => REvent}
import intelmedia.ws.funnel.Events.Event
import java.net.URL
import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Strategy,Task,Actor}
import scalaz.stream.{async,Process}
import Monitoring.prettyURL
import scala.collection.JavaConverters._

object Riemann {

  sealed trait Pusher
  final case class Hold(e: REvent) extends Pusher
  final case object Flush extends Pusher

  @volatile private var store: List[REvent] = Nil

  private[riemann] def collector(
    R: RiemannClient
  )(implicit log: String => Unit): Actor[Pusher] = {
    val a = Actor.actor[Pusher] {
      case Hold(e) => store = (e :: store)
      case Flush   => {
        R.sendEvents(store.asJava)
        log(s"successfully sent batch of ${store.length}")
        store = Nil
      }
    }(Strategy.Executor(Monitoring.serverPool))

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
  // def retry[A](retries: Process[Task,Any])(p: Process[Task,A]): Process[Task,A] = {
  //   val alive = SampledSignal[Unit]
  //   Process.eval_(alive.set(())) ++ {
  //     val step: Process[Task,Throwable \/ A] =
  //       p.append(Process.eval_(alive.close)).attempt()

  //     step.flatMap(_.fold(_ => link(alive)(retries).terminated.flatMap {
  //       // on our last reconnect attempt, rethrow error
  //       case None => step.flatMap(_.fold(Process.fail, Process.emit))
  //       // on other attempts, ignore the exceptions
  //       case Some(_) => step.stripW
  //     }, Process.emit))
  //   }
  // }

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

  def defaultRetries = Events.takeEvery(30 seconds, 6)

  case class Names(kind: String, mine: String, theirs: String)

  private val urlSignals = new java.util.concurrent.ConcurrentHashMap[URL, SampledSignal[Unit]]


  /**
   * Mirror the supplied funnel servers into the local monitoring instance
   * and publish the values to riemann in batches. This function also controls
   * the halting of stream consumption (e.g. in the event of failure and
   * explicit termination by the administraton `ControlServer`)
   */
  def mirrorAndPublish[A](
    M: Monitoring,
    ttlInSeconds: Float = 20f,
    nodeRetries: Names => Event = _ => defaultRetries
  )(riemannClient: RiemannClient,
    riemannName: String,
    riemannRetries: Names => Event = _ => defaultRetries)(
    parse: DatapointParser)(
    groupedUrls: Process[Task, Command],
    myName: String = "Funnel Mirror"
  )(implicit log: String => Unit): Task[Unit] = {

    val S = Strategy.Executor(Monitoring.defaultPool)
    // val alive = SampledSignal[Unit](S)
    val active = SampledSignal[Set[URL]](S)

    def modifyActive(f: Set[URL] => Set[URL]): Task[Unit] =
      active.compareAndSet(a => Some(f(a.getOrElse(Set())))).map(_ => ())

    val actor = collector(riemannClient)

    (for {
      // initilize the signal for the current running set of
      // urls being monitored
      _ <- active.set(Set.empty)
      // _ <- alive.set(())
      _ <- groupedUrls.evalMap {
        case Mirror(url,group) => Task.delay {
          log("recieved url: " + group + " / " + url)
          log("existing urls: " + active.get.run)

          val hook = SampledSignal[Unit](S)

          urlSignals.put(url, hook)

          // adding the `localName` onto the string here so that later in the
          // process its possible to find the key we're specifically looking for
          // and trim off the `localName`
          val localName = prettyURL(url)

          val received: Process[Task,Unit] = link(hook){
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
        case Discard(url) => Task.delay {
          Option(urlSignals.get(url)).foreach(_.close)
        }
      }.run
      // _ <- alive.close
    } yield ()).runAsync(_ => ())

    publish(M, ttlInSeconds, riemannRetries(Names("Riemann", myName, riemannName)))(riemannClient, actor)
  }
}
