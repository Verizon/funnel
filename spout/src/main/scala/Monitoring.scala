package intelmedia.ws.monitoring

import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}
import scala.concurrent.duration._
import scala.language.higherKinds
import scalaz.concurrent.{Actor,Strategy,Task}
import scalaz.Nondeterminism
import scalaz.stream._
import scalaz.stream.async
import scalaz.{~>, Monad}

/**
 * A hub for publishing and subscribing to streams
 * of values.
 */
trait Monitoring {
  import Monitoring._

  /**
   * Create a new topic with the given name and units,
   * using a stream transducer to
   */
  def topic[I, O:Reportable](
      name: String, units: Units[O])(
      buf: Process1[(I,Duration),O]): (Key[O], I => Unit) = {
    val k = Key[O](name, units)
    (k, topic(k)(buf))
  }

  protected def topic[I,O](key: Key[O])(buf: Process1[(I,Duration),O]): I => Unit

  /**
   * Return the continuously updated signal of the current value
   * for the given `Key`. Use `get(k).discrete` to get the
   * discrete stream of values for this key, updated only
   * when new values are produced.
   */
  def get[O](k: Key[O]): async.immutable.Signal[O]

  /** Convience function to publish a metric under a newly created key. */
  def publish[O:Reportable](name: String, units: Units[O])(
                            events: Process[Task,Unit])(
                            f: Metric[O]): Task[Key[O]] =
    publish(Key(name, units))(events)(f)

  /**
   * Publish a metric with the given name on every tick of `events`.
   * See `Events` for various combinators for building up possible
   * arguments to pass here (periodically, when one or more keys
   * change, etc).
   *
   * This method does not check for uniqueness.
   */
  def publish[O](key: Key[O])(events: Process[Task,Unit])(f: Metric[O]): Task[Key[O]] = Task.delay {
    if (exists(key).run) sys.error("key not unique, use republish if this is intended: " + key)
    // `trans` is a polymorphic fn from `Key` to `Task`, picks out
    // latest value for that `Key`
    val trans = new (Key ~> Task) {
      def apply[A](k: Key[A]): Task[A] = latest(k)
    }
    // Invoke Metric interpreter, giving it function from Key to Task
    val refresh: Task[O] = f.run(trans)
    // Whenever `event` generates a new value, refresh the signal
    val proc: Process[Task, O] = events.flatMap(_ => Process.eval(refresh))
    // And finally republish these values to a new topic
    val snk = topic[O,O](key)(Buffers.ignoreTime(process1.id))
    proc.map(snk).run.runAsync(_ => ()) // nonblocking
    key
  }

  /**
   * Update the current value associated with the given `Key`. Implementation
   * detail, this should not be used by clients.
   */
  protected def update[O](k: Key[O], v: O): Task[Unit]

  // could have mirror(events)(url, prefix), which is polling
  // rather than pushing

  /**
   * Mirror the (assumed) unique event at the given url and prefix.
   * Example: `mirror[String]("http://localhost:8080", "now/health")`.
   * This will fetch the stream at `http://localhost:8080/stream/now/health`
   * and keep it updated locally, as events are published at `url`.
   * `localName` may be (optionally) supplied to change the name of the
   * key used locally. The `id` field of the key is preserved, unless
   * `clone` is set to `true`.
   *
   * This function checks that the given `prefix` uniquely determines a
   * key, and that it has the expected type, and fails fast otherwise.
   */
  def mirror[O:Reportable](url: String, prefix: String, localName: Option[String] = None)(
      implicit S: ExecutorService = Monitoring.serverPool,
               log: String => Unit = println): Task[Key[O]] =
    SSE.readEvent(url, prefix)(implicitly[Reportable[O]], S).map { case (k, pts) =>
      if (exists(k).run) sys.error("cannot mirror pre-existing key: " + k)
      val key = localName.map(k.rename(_)).getOrElse(k)
      val snk = topic[O,O](key)(Buffers.ignoreTime(process1.id))
      // send to sink asynchronously, this will not block
      log("spawning updates")
      pts.evalMap { pt =>
        import JSON._
        log("got datapoint: " + pt)
        Task { snk(pt.value) } (S)
      }.run.runAsync(_ => ())
      key
    }

  /**
   * Mirror all metrics from the given URL, adding `localPrefix` onto the front of
   * all loaded keys.
   */
  def mirrorAll(url: String, localPrefix: String = "")(
                implicit S: ExecutorService = Monitoring.serverPool,
                log: String => Unit = println): Process[Task,Unit] = {
    SSE.readEvents(url).flatMap { pt =>
      if (exists(pt.key).run) {
        log(s"mirrorAll - new key: ${pt.key}")
        log(s"mirrorAll - got: $pt")
        Process.eval(update(pt.key, pt.value))
      }
      else {
        log(s"mirrorAll - got: $pt")
        val key = pt.key.rename(localPrefix + pt.key.name)
        val snk = topic[Any,Any](key)(Buffers.ignoreTime(process1.id))
        Process.emit(snk(pt.value))
      }
    }
  }

  /** Return the elapsed time since this instance was started. */
  def elapsed: Duration

  /** Return the most recent value for a given key. */
  def latest[O](k: Key[O]): Task[O] =
    get(k).continuous.once.runLast.map(_.get)

  /** The time-varying set of keys. */
  def keys: async.immutable.Signal[List[Key[Any]]]

  /** Returns `true` if the given key currently exists. */
  def exists[O](k: Key[O]): Task[Boolean] = keys.continuous.once.runLastOr(List()).map(_.contains(k))

  /** Attempt to uniquely resolve `name` to a key of some expected type. */
  def lookup[O](name: String)(implicit R: Reportable[O]): Task[Key[O]] =
    keysByName(name).once.runLastOr(List()).map {
      case List(k) =>
        val t = k.typeOf
        if (t == R) k.asInstanceOf[Key[O]]
        else sys.error("type mismatch: $R $t")
      case ks => sys.error(s"lookup($name) does not determine a unique key: $ks")
    }

  // def aggregateEvery[O,O2](name: String)(summarize: Seq[Key[O]] => Metric[O2])(implicit R: Reportable[O]):

  /** The infinite discrete stream of unique keys, as they are added. */
  def distinctKeys: Process[Task, Key[Any]] =
    keys.discrete.flatMap(Process.emitAll).pipe(Buffers.distinct)

  /** Create a new topic with the given name and discard the key. */
  def topic_[I, O:Reportable](
    name: String, units: Units[O])(
    buf: Process1[(I,Duration),O]): I => Unit = topic(name, units)(buf)._2

  def keysByName(name: String): Process[Task, List[Key[Any]]] =
    keys.continuous.map(_.filter(_ matches name))
}

object Monitoring {

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  val defaultPool: ExecutorService =
    Executors.newFixedThreadPool(8, daemonThreads("monitoring-thread"))

  val serverPool: ExecutorService =
    Executors.newCachedThreadPool(daemonThreads("monitoring-http-server"))

  val schedulingPool: ScheduledExecutorService =
    Executors.newScheduledThreadPool(4, daemonThreads("monitoring-scheduled-tasks"))

  val default: Monitoring = instance(defaultPool)

  def instance(implicit ES: ExecutorService = defaultPool): Monitoring = {
    import async.immutable.Signal
    import scala.collection.concurrent.TrieMap
    val t0 = System.nanoTime
    val S = Strategy.Executor(ES)
    val P = Process
    val keys_ = async.signal[List[Key[Any]]](S)
    keys_.value.set(List())

    case class Topic[I,O](
      publish: ((I,Duration)) => Unit,
      current: async.mutable.Signal[O]
    )
    val topics = new TrieMap[Key[Any], Topic[Any,Any]]()
    val us = new TrieMap[Key[Any], (Reportable[Any], Units[Any])]()

    def eraseTopic[I,O](t: Topic[I,O]): Topic[Any,Any] = t.asInstanceOf[Topic[Any,Any]]

    new Monitoring {
      def keys = keys_

      def topic[I,O](k: Key[O])(buf: Process1[(I,Duration),O]): I => Unit = {
        val (pub, v) = bufferedSignal(buf)(ES)
        topics += (k -> eraseTopic(Topic(pub, v)))
        val t = (k.typeOf, k.units)
        us += (k -> t)
        keys_.value.modify(k :: _)
        (i: I) => pub(i -> Duration.fromNanos(System.nanoTime - t0))
      }

      protected def update[O](k: Key[O], v: O): Task[Unit] = Task.delay {
        us.get(k).map(_._1.cast(k.typeOf)).flatMap { _ =>
          topics.get(k).map(_.current.value.set(v))
        } getOrElse (sys.error("key types did not match"))
      }


      def get[O](k: Key[O]): Signal[O] =
        topics.get(k).map(_.current.asInstanceOf[Signal[O]])
                     .getOrElse(sys.error("key not found: " + k))

      def units[O](k: Key[O]): Units[O] =
        us.get(k).map(_._2.asInstanceOf[Units[O]])
                 .getOrElse(sys.error("key not found: " + k))

      def typeOf[O](k: Key[O]): Reportable[O] =
        us.get(k).map(_._1.asInstanceOf[Reportable[O]])
                 .getOrElse(sys.error("key not found: " + k))

      def elapsed: Duration = Duration.fromNanos(System.nanoTime - t0)
    }
  }

  /**
   * Return a discrete stream of updates to all keys
   * matching the given prefix. Note that:
   *
   *   a) There is no queueing of producer updates,
   *      so a 'slow' consumer can miss updates.
   *   b) The returned stream is 'use-once' and will
   *      halt the producer when completed. Just
   *      resubscribe if you need a fresh stream.
   */
  def subscribe(M: Monitoring)(prefix: String, log: String => Unit = println)(
  implicit ES: ExecutorService = serverPool):
      Process[Task, Datapoint[Any]] =
    Process.suspend { // don't actually do anything until we have a consumer
      val S = Strategy.Executor(ES)
      val out = scalaz.stream.async.signal[Datapoint[Any]](S)
      val alive = scalaz.stream.async.signal[Boolean](S)
      val heartbeat = alive.continuous.takeWhile(identity)
      alive.value.set(true)
      S { // in the background, populate the 'out' `Signal`
        alive.discrete.map(!_).wye(M.distinctKeys)(wye.interrupt)
        .filter(_.matches(prefix))
        .map { k =>
          // asynchronously set the output
          S { M.get(k).discrete
               .map(v => out.value.set(Datapoint(k, v)))
               .zip(heartbeat)
               .onComplete { Process.eval_{ Task.delay(log("unsubscribing: " + k))} }
               .run.run
            }
        }.run.run
        log("killed producer for prefix: " + prefix)
      }
      // kill the producers when the consumer completes
      out.discrete onComplete {
        Process.eval_ { Task.delay {
          log("killing producers for prefix: " + prefix)
          out.close
          alive.value.set(false)
        }}
      }
    }

  /**
   * Obtain the latest values for all active metrics.
   */
  def snapshot(M: Monitoring)(implicit ES: ExecutorService = defaultPool):
    Task[collection.Map[Key[Any], Datapoint[Any]]] = {
    val m = collection.concurrent.TrieMap[Key[Any], Datapoint[Any]]()
    val S = Strategy.Executor(ES)
    for {
      ks <- M.keys.continuous.once.runLastOr(List())
      t <- Nondeterminism[Task].gatherUnordered {
        ks.map(k => M.get(k).continuous.once.runLast.map(
          _.map(v => k -> Datapoint(k, v))
        ).timed(100L).attempt.map(_.toOption))
      }
      _ <- Task { t.flatten.flatten.foreach(m += _) }
    } yield m
  }

  /**
   * Send values through a `Process1[I,O]` to a `Signal[O]`, which will
   * always be equal to the most recent value produced by `buf`.
   */
  private[monitoring] def bufferedSignal[I,O](
      buf: Process1[I,O])(
      implicit ES: ExecutorService = defaultPool):
      (I => Unit, async.mutable.Signal[O]) = {
    val signal = async.signal[O](Strategy.Executor(ES))
    var cur = buf.unemit match {
      case (h, t) if h.nonEmpty => signal.value.set(h.last); t
      case (h, t) => t
    }
    val hub = Actor.actor[I] { i =>
      val (h, t) = process1.feed1(i)(cur).unemit
      if (h.nonEmpty) signal.value.set(h.last)
      cur = t
      cur match {
        case Process.Halt(e) => signal.value.fail(e)
        case _ => ()
      }
    } (Strategy.Sequential)
    ((i: I) => hub ! i, signal)
  }

}

