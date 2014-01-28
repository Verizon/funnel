package intelmedia.ws.funnel

import java.net.{URL,URLEncoder}
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}
import scala.concurrent.duration._
import scala.language.higherKinds
import scalaz.concurrent.{Actor,Strategy,Task}
import scalaz.Nondeterminism
import scalaz.stream._
import scalaz.stream.async
import scalaz.{\/, ~>, Monad}
import Events.Event

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
      buf: Process1[(I,Duration),O]): (Key[O], I => SafeUnit) = {
    val k = Key[O](name, units)
    (k, topic(k)(buf))
  }

  protected def topic[I,O](key: Key[O])(buf: Process1[(I,Duration),O]): I => SafeUnit

  /**
   * Return the continuously updated signal of the current value
   * for the given `Key`. Use `get(k).discrete` to get the
   * discrete stream of values for this key, updated only
   * when new values are produced.
   */
  def get[O](k: Key[O]): async.immutable.Signal[O]

  /** Convience function to publish a metric under a newly created key. */
  def publish[O:Reportable](name: String, units: Units[O])(e: Event)(
                            f: Metric[O]): Task[Key[O]] =
    publish(Key(name, units))(e)(f)

  /**
   * Like `publish`, but if `key` is preexisting, sends updates
   * to it rather than throwing an exception.
   */
  def republish[O](key: Key[O])(e: Event)(f: Metric[O]): Task[Key[O]] = Task.delay {
    val refresh: Task[O] = eval(f)
    // Whenever `event` generates a new value, refresh the signal
    val proc: Process[Task, O] = e(this).flatMap(_ => Process.eval(refresh))
    // Republish these values to a new topic
    val snk =
      if (!exists(key).run) topic[O,O](key)(Buffers.ignoreTime(process1.id))
      else (o: O) => update(key, o).runAsync(_ => ())
    proc.map(snk).run.runAsync(_ => ()) // nonblocking
    key
  }

  /**
   * Publish a metric with the given name on every tick of `events`.
   * See `Events` for various combinators for building up possible
   * arguments to pass here (periodically, when one or more keys
   * change, etc). Example `publish(k)(Events.every(5 seconds))(
   *
   * This method checks that the given key is not preexisting and
   * throws an error if the key already exists. Use `republish` if
   * preexisting `key` is not an error condition.
   */
  def publish[O](key: Key[O])(e: Event)(f: Metric[O]): Task[Key[O]] = Task.suspend {
    if (exists(key).run) sys.error("key not unique, use republish if this is intended: " + key)
    else republish(key)(e)(f)
  }

  /** Compute the current value for the given `Metric`. */
  def eval[A](f: Metric[A]): Task[A] = {
    // `trans` is a polymorphic fn from `Key` to `Task`, picks out
    // latest value for that `Key`
    val trans = new (Key ~> Task) {
      def apply[A](k: Key[A]): Task[A] = latest(k)
    }
    // Invoke Metric interpreter, giving it function from Key to Task
    f.run(trans)
  }

  /**
   * Update the current value associated with the given `Key`. Implementation
   * detail, this should not be used by clients.
   */
  protected def update[O](k: Key[O], v: O): Task[SafeUnit]

  /**
   * Mirror all metrics from the given URL, adding `localPrefix` onto the front of
   * all loaded keys. `url` is assumed to be a stream of datapoints in SSE format.
   */
  def mirrorAll(parse: DatapointParser)(
                url: URL, localName: String => String = identity)(
                implicit S: ExecutorService = Monitoring.serverPool,
                log: String => SafeUnit): Process[Task,SafeUnit] = {
    parse(url).flatMap { pt =>
      val msg = "Monitoring.mirrorAll:" // logging msg prefix
      val k = pt.key.modifyName(localName)
      if (exists(k).run) {
        // log(s"$msg got $pt")
        Process.eval(update(k, pt.value))
      }
      else {
        log(s"$msg new key ${pt.key}")
        // log(s"$msg got $pt")
        val snk = topic[Any,Any](k)(Buffers.ignoreTime(process1.id))
        Process.emit(snk(pt.value))
      }
    }
  }

  /**
   * Like `mirrorAll`, but tries to reconnect periodically, using
   * the schedule set by `breaker`. Example:
   * `attemptMirrorAll(Events.takeEvery(3 minutes, 5))(url, prefix)`
   * will call `mirrorAll`, and retry every three minutes up to
   * 5 attempts before raising the most recent exception.
   */
  def attemptMirrorAll(
      parse: DatapointParser)(
      breaker: Event)(
      url: URL, localName: String => String = identity)(
        implicit S: ExecutorService = Monitoring.serverPool,
        log: String => SafeUnit): Process[Task,SafeUnit] = {
    val report = (e: Throwable) => {
      log("attemptMirrorAll.ERROR: "+e)
      ()
    }
    Monitoring.attemptRepeatedly(report)(
      mirrorAll(parse)(url, localName))(breaker(this))
  }

  /**
   * Given a stream of URLs with associated group names, mirror all
   * metrics from these URLs, and aggregate the `health` key for
   * clusters of nodes with the same name. `reconnectFrequency` controls reconnect
   * attempts. Example:
   *
   * {{{
   * val parser: DatapointParser = url => ....
   *
   * // when there's a failure connecting to a node, define the reconnect frequency
   * val reconnect   = Events.every(2 minutes)
   *
   * // if the url has not produced any updates in this duration/event cycle,
   * // reset the values to their defaults after this bound
   * val decay       = Event.every(15 seconds)
   *
   * // how frequently to produce the "aggregate" health `key` for each group of urls
   * val aggregating = Event.every(5 seconds)
   *
   * mirrorAndAggregate(parser)(reconnect,decay,aggregating)(urls)(
   *   Key[Boolean]("health", Units.Healthy)) {
   *     case "accounts" => Policies.quorum(2) _
   *     case "decoding" => Policies.majority _
   *     case "blah"     => Policies.all _
   *   }
   * }}}
   */
  def mirrorAndAggregate[A](
      parse: DatapointParser)(
      reconnectFrequency: Event,
      decayFrequency: Event,
      aggregateFrequency: Event)(
      groupedUrls: Process[Task, (URL,String)],
      health: Key[A])(f: String => Seq[A] => A)(
      implicit log: String => SafeUnit): Task[SafeUnit] =
    Task.delay {
      // use urls as the local names for keys
      var seen = Set[String]()
      var seenURLs = Set[(URL,String)]()
      groupedUrls.evalMap { case (url,group) => Task.delay {
        if (!seen.contains(group)) {
          val aggregateKey = health.modifyName(group + "/" + _)
          val keyFamily = health.modifyName(x => s"$group/$x/")
          aggregate(keyFamily, aggregateKey)(aggregateFrequency)(f(group)).run
          seen = seen + group
        }
        val localName = prettyURL(url)
        if (!seenURLs.contains(url -> group)) {
          decay(Key.EndsWith(localName))(decayFrequency).run
          seenURLs = seenURLs + (url -> group)
        }
        // ex - `now/health` ==> `accounts/now/health/192.168.2.1`
        attemptMirrorAll(parse)(reconnectFrequency)(
          url, m => s"$group/$m/$localName"
        ).run.runAsync(_ => ())
      }}.run.runAsync(_ => ())
    }

  private def initialize[O](key: Key[O]): SafeUnit = {
    if (!exists(key).run) topic[O,O](key)(Buffers.ignoreTime(process1.id))
    ()
  }

  /**
   * Publish a new metric by aggregating all keys in the given family.
   * This just calls `evalFamily(family)` on each tick of `e`, and
   * publishes the result of `f` to the output key `out`.
   */
  def aggregate[O,O2](family: Key[O], out: Key[O2])(e: Event)(
                      f: Seq[O] => O2)(
                      implicit log: String => SafeUnit = _ => ()): Task[Key[O2]] = Task.delay {
    initialize(out)
    e(this).flatMap { _ =>
      log("Monitoring.aggregate: gathering values")
      Process.eval { evalFamily(family).flatMap { vs =>
        val v = f(vs)
        log(s"Monitoring.aggregate: aggregated $v from ${vs.length} matching keys")
        update(out, v)
      }}
    }.run.runAsync(_ => ())
    out
  }

  /**
   * Reset all keys matching the given prefix back to their default
   * values if they receive no updates between ticks of `e`. Example:
   * `decay("node1/health")(Event.every(10 seconds))` would set the
   * `node1/health` metric(s) to `false` if no new values are published
   * within a 10 second window. See `Units.default`.
   */
  def decay(f: Key[Any] => Boolean)(e: Event)(
            implicit log: String => SafeUnit): Task[SafeUnit] = Task.delay {
    def reset = keys.continuous.once.map {
      _.foreach(k => k.default.foreach(update(k, _).run))
    }.run
    val msg = "Monitoring.decay:" // logging msg prefix

    // we merge the `e` stream and the stream of datapoints for the
    // given prefix; if we ever encounter two ticks in a row from `e`,
    // we reset all matching keys back to their default
    val alive = async.signal[SafeUnit](Strategy.Sequential); alive.value.set(())
    val pts = Monitoring.subscribe(this)(f).onComplete {
      Process.eval_ { alive.close flatMap { _ =>
        log(s"$msg no more data points for '$f', resetting...")
        reset
      }}
    }
    e(this).zip(alive.continuous).map(_._1).either(pts)
           .scan(Vector(false,false))((acc,a) => acc.tail :+ a.isLeft)
           .filter { xs => xs forall (identity) }
           .evalMap { _ => log(s"$msg no activity for '$f', resetting..."); reset }
           .run.runAsync { _ => () }
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
    filterKeys(Key.StartsWith(name)).once.runLastOr(List()).map {
      case List(k) =>
        val t = k.typeOf
        if (t == R) k.asInstanceOf[Key[O]]
        else sys.error("type mismatch: $R $t")
      case ks => sys.error(s"lookup($name) does not determine a unique key: $ks")
    }

  /** The infinite discrete stream of unique keys, as they are added. */
  def distinctKeys: Process[Task, Key[Any]] =
    keys.discrete.flatMap(Process.emitAll).pipe(Buffers.distinct)

  /** Create a new topic with the given name and discard the key. */
  def topic_[I, O:Reportable](
    name: String, units: Units[O])(
    buf: Process1[(I,Duration),O]): I => SafeUnit = topic(name, units)(buf)._2

  def filterKeys(f: Key[Any] => Boolean): Process[Task, List[Key[Any]]] =
    keys.continuous.map(_.filter(f))

  /**
   * Returns the continuous stream of values for keys whose type
   * and units match `family`, and whose name is prefixed by
   * `family.name`. The sequences emitted are in no particular order.
   */
  def evalFamily[O](family: Key[O]): Task[Seq[O]] =
    filterKeys(Key.StartsWith(family.name)).once.runLastOr(List()).flatMap { ks =>
      val ksO: Seq[Key[O]] = ks.flatMap(_.cast(family.typeOf, family.units))
      Nondeterminism[Task].gatherUnordered(ksO map (k => eval(k)))
    }
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
    Executors.newCachedThreadPool(daemonThreads("monitoring-server"))

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
      publish: ((I,Duration)) => SafeUnit,
      current: async.mutable.Signal[O]
    )
    val topics = new TrieMap[Key[Any], Topic[Any,Any]]()

    def eraseTopic[I,O](t: Topic[I,O]): Topic[Any,Any] = t.asInstanceOf[Topic[Any,Any]]

    new Monitoring {
      def keys = keys_

      def topic[I,O](k: Key[O])(buf: Process1[(I,Duration),O]): I => SafeUnit = {
        val (pub, v) = bufferedSignal(buf)(ES)
        topics += (k -> eraseTopic(Topic(pub, v)))
        val t = (k.typeOf, k.units)
        keys_.value.modify(k :: _)
        (i: I) => pub(i -> Duration.fromNanos(System.nanoTime - t0))
      }

      protected def update[O](k: Key[O], v: O): Task[SafeUnit] = Task.delay {
        topics.get(k).foreach(_.current.value.set(v))
      }

      def get[O](k: Key[O]): Signal[O] =
        topics.get(k).map(_.current.asInstanceOf[Signal[O]])
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
  def subscribe(M: Monitoring)(f: Key[Any] => Boolean)(
  implicit ES: ExecutorService = serverPool,
           log: String => SafeUnit):
      Process[Task, Datapoint[Any]] =
    Process.suspend { // don't actually do anything until we have a consumer
      val S = Strategy.Executor(ES)
      val out = scalaz.stream.async.signal[Datapoint[Any]](S)
      val alive = scalaz.stream.async.signal[Boolean](S)
      val heartbeat = alive.continuous.takeWhile(identity)
      alive.value.set(true)
      S { // in the background, populate the 'out' `Signal`
        alive.discrete.map(!_).wye(M.distinctKeys)(wye.interrupt)
        .filter(f)
        .map { k =>
          // asynchronously set the output
          S { M.get(k).discrete
               .map(v => out.value.set(Datapoint(k, v)))
               .zip(heartbeat)
               .onComplete { Process.eval_{ Task.delay(log("unsubscribing: " + k))} }
               .run.run
            }
        }.run.run
        log("killed producer for: " + f)
      }
      // kill the producers when the consumer completes
      out.discrete onComplete {
        Process.eval_ { Task.delay {
          log("killing producers for: " + f)
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
  private[funnel] def bufferedSignal[I,O](
      buf: Process1[I,O])(
      implicit ES: ExecutorService = defaultPool):
      (I => SafeUnit, async.mutable.Signal[O]) = {
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

  private[funnel] def attemptRepeatedly[A](
    maskedError: Throwable => Unit)(
    p: Process[Task,A])(
    schedule: Process[Task,Unit]): Process[Task,A] = {
    val step: Process[Task, Throwable \/ A] =
      p.attempt(e => Process.eval { Task.delay { maskedError(e); e }})
    step.stripW ++ schedule.terminated.flatMap {
      // on our last reconnect attempt, rethrow error
      case None => step.flatMap(_.fold(Process.fail, Process.emit))
      // on other attempts, ignore the exceptions
      case Some(_) => step.stripW
    }
  }

  private[funnel] def prettyURL(url: URL): String = {
    val host = url.getHost
    val path = url.getPath
    val port = url.getPort match {
      case -1 => ""
      case x => "-"+x
    }
    host + port + path
  }
}

