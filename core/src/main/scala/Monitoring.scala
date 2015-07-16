package funnel

import java.net.URI
import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory, ConcurrentHashMap}
import scala.concurrent.duration._
import scala.language.higherKinds
import scalaz.concurrent.{Actor,Strategy,Task}
import scalaz.{Nondeterminism,==>>}
import scalaz.stream._
import scalaz.stream.merge._
import scalaz.stream.async
import async.mutable.Queue
import scalaz.syntax.traverse._
import scalaz.syntax.monad._
import scalaz.std.option._
import scalaz.std.string._
import scalaz.std.set._
import scalaz.{\/, ~>, Monad}
import Events.Event
import scalaz.stream.async.mutable.Signal
import scalaz.stream.async.{signalOf,signalUnset}
import journal.Logger
import internals._

/**
 * A hub for publishing and subscribing to streams
 * of values.
 */
trait Monitoring {
  import Monitoring._

  def log: Logger

  /**
   * Create a new topic with the given name and units,
   * using a stream transducer to
   */
  def topic[I, O:Reportable](
      name: String, units: Units, description: String, keyMod: Key[O] => Key[O])(
      buf: Process1[(I,Duration),O]): (Key[O], Task[I => Task[Unit]]) = {
    val k = keyMod(Key[O](name, units, description))
    (k, topic(k)(buf))
  }

  protected def topic[I,O](key: Key[O])(buf: Process1[(I,Duration),O]): Task[I => Task[Unit]]

  /**
   * Return the continuously updated signal of the current value
   * for the given `Key`. Use `get(k).discrete` to get the
   * discrete stream of values for this key, updated only
   * when new values are produced.
   */
  def get[O](k: Key[O]): Signal[O]

  /**
   * Remove the given key and stop updating it.
   */
  def remove[O](k: Key[O]): Task[Unit]

  /** Convience function to publish a metric under a newly created key. */
  def publish[O:Reportable](name: String, units: Units)(e: Event)(
                            f: Metric[O]): Task[Key[O]] =
    publish(Key(name, units))(e)(f)

  /**
   * Like `publish`, but if `key` is preexisting, sends updates
   * to it rather than throwing an exception.
   */
  def republish[O](key: Key[O])(e: Event)(f: Metric[O]): Task[Key[O]] = Task.suspend {
    val refresh: Task[O] = eval(f)
    // Whenever `event` generates a new value, refresh the signal
    val proc: Process[Task, O] = e(this).flatMap(_ => Process.eval(refresh))
    // Republish these values to a new topic
    for {
      _ <- proc.evalMap((o: O) => for {
        b <- exists(key)
        _ <- if (b) Task.delay(Task.fork(update(key, o))(defaultPool).runAsync(_ => ()))
             else Task(topic[O,O](key)(Buffers.ignoreTime(process1.id)))(defaultPool)
      } yield ()).run
    } yield key
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
  def publish[O](key: Key[O])(e: Event)(f: Metric[O]): Task[Key[O]] =
    for {
      b <- exists(key)
      k <- if (b) Task.fail(new Exception(s"key not unique, use republish if this is indented: $key"))
           else republish(key)(e)(f)
    } yield k

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
  protected def update[O](k: Key[O], v: O): Task[Unit]

  private[funnel] val mirroringQueue =
    async.unboundedQueue[Command](Strategy.Executor(Monitoring.serverPool))

  private[funnel] val mirroringCommands: Process[Task, Command] = mirroringQueue.dequeue observe(io.stdOut.contramap[Command](_.toString))

  private val urlSignals = new ConcurrentHashMap[URI, Signal[Unit]]

  private val clusterUrls = new Ref[ClusterName ==>> Set[URI]](==>>())

  /**
   * Fetch a list of all the URLs that are currently being mirrored.
   * If nothing is currently being mirrored (as is the case for all funnels)
   * then this method yields an empty `Set[URL]`.
   */
  def mirroringUrls: List[(ClusterName, List[String])] = {
    clusterUrls.get.toList.map { case (k,s) =>
      k -> s.toList.map(_.toString)
    }
  }

  /** Terminate `p` when the given `Signal` terminates. */
  def link[A](alive: Signal[Unit])(p: Process[Task,A]): Process[Task,A] =
    alive.continuous.zip(p).map(_._2)

  def processMirroringEvents(
    parse: DatapointParser,
    Q: Queue[Telemetry],
    myName: String = "Funnel Mirror",
    nodeRetries: Names => Event = _ => defaultRetries
  ): Task[Unit] = {
    val S = Strategy.Executor(Monitoring.defaultPool)
    val alive     = signalOf[Unit](())(S)
    val active    = signalOf[Set[URI]](Set.empty)(S)

    /**
     * Update the running state of the world by updating the URLs we know about
     * to mirror, and the cluster -> url mapping.
     */
    def modifyActive(b: ClusterName, f: Set[URI] => Set[URI]): Task[Unit] = {
      for {
        _ <- active.compareAndSet(a => Option(f(a.getOrElse(Set.empty[URI]))))
        _ <- Task(clusterUrls.update(_.alter(b, s => Option(f(s.getOrElse(Set.empty[URI]))))).filter(!_.isEmpty))(defaultPool)
        _  = log.debug(s"modified the active uri set for $b: ${clusterUrls.get.lookup(b).getOrElse(Set.empty)}")
      } yield ()
    }

    for {
      _ <- mirroringCommands.evalMap {
        case Mirror(source, cluster) => Task.suspend {
          log.info(s"Attempting to monitor '$cluster' located at '$source'")
          val S = Strategy.Executor(Monitoring.serverPool)
          val hook = signalOf[Unit](())(S)

          urlSignals.put(source, hook)

          val received: Process[Task,Unit] = link(hook) {
            attemptMirrorAll(parse)(nodeRetries(Names(cluster, myName, new URI(source.toString))))(
              source, Map(AttributeKeys.cluster -> cluster, AttributeKeys.source -> source.toString))
          }

          val receivedIdempotent = Process.eval(active.get).flatMap { urls =>
            if (urls.contains(source)) {
              log.info(s"Skipping $source, already mirrored")
              Process.halt
            }
            else Process.eval_ {
              modifyActive(cluster, _ + source) >>
              Q.enqueueOne(Monitored(source)) >>
              Task.delay(log.info(s"Enqueued monitored $source on telemetry queue"))
            } ++ received.onComplete(Process.eval_ {
              Q.enqueueOne(Problem(source, "")) >>
              Task.delay(log.info(s"Enqueued problem with $source on telemetry queue"))
            })
          }

          Task.delay(logErrors(Task.fork(receivedIdempotent.run)(defaultPool)).runAsync(_ => ()))
        }
        case Discard(source) => for {
          _ <- Task.delay { log.info(s"Attempting to stop monitoring $source...") }
          _ <- Option(urlSignals.get(source)).traverse(_.close)
        } yield ()
      }.run
      _ <- alive.close
    } yield ()
  }

  def logErrors[A](t: Task[A]) =
    t.attempt.flatMap(_.fold(e => Task.delay(log.error(e.getMessage)), Task.now))

  /**
   * Mirror all metrics from the given URL, adding `localPrefix` onto the front of
   * all loaded keys. `url` is assumed to be a stream of datapoints in SSE format.
   */
  def mirrorAll(parse: DatapointParser)(
                source: URI, attrs: Map[String,String] = Map())(
                implicit S: ExecutorService = Monitoring.serverPool): Process[Task,Unit] = {
    parse(source).evalMap { pt =>
      val msg = "Monitoring.mirrorAll:" // logging msg prefix
      val k = pt.key.withAttributes(attrs)
      for {
        b <- exists(k)
        _ <- if (b) {
          update(k, pt.value)
        } else for {
          _ <- Task.delay(log.debug(s"$msg new key: $k"))
          snk <- topic[Any,Any](k)(Buffers.ignoreTime(process1.id))
          r <- snk(pt.value)
        } yield r
      } yield ()
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
      parse: DatapointParser)(breaker: Event)(source: URI, attrs: Map[String, String] = Map())(
        implicit S: ExecutorService = Monitoring.serverPool): Process[Task,Unit] = {
    val report = (e: Throwable) => {
      log.error(s"attemptMirrorAll.ERROR: source: $source, error: $e")
      ()
    }
    Monitoring.attemptRepeatedly(report)(
      mirrorAll(parse)(source, attrs))(breaker(this))
  }

  private def initialize[O](key: Key[O]): Task[Unit] = for {
    e <- exists(key)
    _ <- if (e) Task.delay {
      topic[O,O](key)(Buffers.ignoreTime(process1.id))
    } else Task((o: O) => ())(defaultPool)
  } yield ()

  /**
   * Publish a new metric by aggregating all keys in the given family.
   * This just calls `evalFamily(family)` on each tick of `e`, and
   * publishes the result of `f` to the output key `out`.
   */
  def aggregate[O,O2](family: Key[O], out: Key[O2])(e: Event)(
                      f: Seq[O] => O2): Task[Key[O2]] = for {
    _ <- initialize(out)
    _ <- Task.fork(e(this).flatMap { _ =>
      log.debug("Monitoring.aggregate: gathering values")
      Process.eval { evalFamily(family).flatMap { vs =>
        val v = f(vs)
        log.debug(s"Monitoring.aggregate: aggregated $v from ${vs.size} matching keys")
        update(out, v)
      }}}.run)(defaultPool)
  } yield out

  /**
   * Reset all keys matching the given prefix back to their default
   * values if they receive no updates between ticks of `e`. Example:
   * `decay("node1/health")(Event.every(10 seconds))` would set the
   * `node1/health` metric(s) to `false` if no new values are published
   * within a 10 second window. See `Units.default`.
   */
  def decay(f: Key[Any] => Boolean)(e: Event): Task[Unit] = Task.suspend {
    def reset = keys.continuous.once.map {
      _.traverse_(k => k.default.traverse_(update(k, _)))
    }.run
    val msg = "Monitoring.decay:" // logging msg prefix

    // we merge the `e` stream and the stream of datapoints for the
    // given prefix; if we ever encounter two ticks in a row from `e`,
    // we reset all matching keys back to their default
    val alive = signalOf[Unit](())(Strategy.Sequential)
    val pts = Monitoring.subscribe(this)(f).onComplete {
      Process.eval_ { alive.close flatMap { _ =>
        log.debug(s"$msg no more data points for '$f', resetting...")
        reset
      }}
    }
    e(this).zip(alive.continuous).map(_._1).either(pts)
           .scan(Vector(false,false))((acc,a) => acc.tail :+ a.isLeft)
           .filter { xs => xs forall (identity) }
           .evalMap { _ => log.debug(s"$msg no activity for '$f', resetting..."); reset }
           .run
  }

  /**
   * Remove keys from this `Monitoring` instance for which no updates are seen
   * between two triggerings of the event `e`.
   */
  def keySenescence(e: Event, ks: Process[Task, Key[Any]]): Process[Task, Unit] = mergeN(ks.map { k =>
    val alive = signalOf[Unit](())(Strategy.Sequential)
    val pts = get(k).discrete.onComplete {
      Process.eval_ { alive.close flatMap { _ =>
        log.debug(s"Key senescence: no more data points for '${k.name}', removing...")
        remove(k)
      }}
    }
    e(this).zip(alive.continuous).map(_._1).either(pts)
      .scan(Vector(false,false)) {
        (acc, a) => acc.tail :+ a.isLeft
      }.evalMap { v =>
        if (v.forall(identity)) {
          log.debug(s"Key senescence: no activity for '${k.name}' removing...")
          alive.close >> remove(k)
        } else {
          Task.now(())
        }
      }
  }.onComplete {
    Process.eval_(Task.delay(log.debug(s"Key senescence: keys exhausted.")))
  }).onComplete(Process.eval(Task.delay(log.debug(s"Key senescence terminated."))))

  /** Return the elapsed time since this instance was started. */
  def elapsed: Duration

  /** Return the most recent value for a given key. */
  def latest[O](k: Key[O]): Task[O] =
    get(k).continuous.once.runLast.map(_.get)

  /** The time-varying set of keys. */
  def keys: Signal[Set[Key[Any]]]

  /** given some predicate, attempt to see how many keys matching that predicate exist */
  def audit(p: Key[Any] => Option[String]): Task[List[(String, Int)]] =
    keys.compareAndSet(identity).map { k =>
      val ks: List[Key[Any]] = k.toList.flatten
      val clusters: List[String] = ks.flatMap(p(_).toSeq).distinct
      clusters.foldLeft(List.empty[(String, Int)]){ (a,step) =>
        val items = ks.filter(p(_).isDefined)
        (step, items.length) :: a
      }
    }

  /** get a count of all metric keys in the system broken down by their logical prefix **/
  def auditByPrefix: Task[List[(String,Int)]] =
    audit(_.name.split('/').headOption)

  /** get a count of all metric keys in the system broken down by a specified attribute **/
  def auditByAttribute(attribute: String): Task[List[(String,Int)]] =
    audit(_.attributes.get(attribute))

  /** Returns `true` if the given key currently exists. */
  def exists[O](k: Key[O]): Task[Boolean] =
    keys.continuous.once.runLastOr(Set.empty).map(_.contains(k))

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
    keys.discrete.flatMap(keys => Process.emitAll(keys.toSeq)).pipe(Buffers.distinct)

  /** Create a new topic with the given name and discard the key. */
  def topic_[I, O:Reportable](
    name: String, units: Units, description: String,
    buf: Process1[(I,Duration),O]): Task[I => Task[Unit]] =
      topic(name, units, description, identity[Key[O]])(buf)._2

  def filterKeys(f: Key[Any] => Boolean): Process[Task, List[Key[Any]]] =
    keys.continuous.map(_.filter(f).toList)

  /**
   * Returns the continuous stream of values for keys whose type
   * and units match `family`, and whose name is prefixed by
   * `family.name`. The sequences emitted are in no particular order.
   */
  def evalFamily[O](family: Key[O]): Task[Seq[O]] =
    filterKeys(Key.StartsWith(family.name)).once.runLastOr(List()).flatMap { ks =>
      val ksO: Seq[Key[O]] = ks.flatMap(_.cast(family.typeOf, family.units))
      Nondeterminism[Task].gatherUnordered(ksO.map(k => eval(k)).toSeq)
    }

  /**
   * Convenience method for asynchronously running tasks and logging
   * errors using the logger in this monitoring instance.
   */
  def runLogging(x: Task[Unit]) =
    x.runAsync(_.fold(e => log.error(e.getMessage, e), _ => ()))
}

object Monitoring {

  def defaultRetries: Monitoring => Process[Task,Unit] = Events.takeEvery(30 seconds, 6)

  private[funnel] def daemonThreads(name: String) = new ThreadFactory {
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

  val default: Monitoring = instance(defaultPool, printLog)

  private lazy val log = journal.Logger[Monitoring.type]

  private lazy val printLog: String => Unit = { s =>
    log.debug(s)
  }

  def instance(implicit ES: ExecutorService = defaultPool,
               logger: String => Unit = printLog): Monitoring = {
    import scala.collection.concurrent.TrieMap

    val t0 = System.nanoTime
    implicit val S = Strategy.Executor(ES)
    val P = Process
    val keys_ = signalOf[Set[Key[Any]]](Set.empty)(S)

    case class Topic[I,O](
      publish: ((I,Duration)) => Task[Unit],
      current: Signal[O]
    )

    val topics = new TrieMap[Key[Any], Topic[Any,Any]]()

    def eraseTopic[I,O](t: Topic[I,O]): Topic[Any,Any] = t.asInstanceOf[Topic[Any,Any]]

    new Monitoring { self =>
      val log = Logger[Monitoring]

      def keys = keys_

      def topic[I,O](k: Key[O])(buf: Process1[(I,Duration),O]): Task[I => Task[Unit]] = for {
        p <- bufferedSignal(buf)(ES)
        (pub, v) = p
        _ <- Task.delay(topics += (k -> eraseTopic(Topic(pub, v))))
        t = (k.typeOf, k.units)
        _ <- keys_.compareAndSet(_.map(_ + k))
      } yield i => pub(i -> Duration.fromNanos(System.nanoTime - t0))

      protected def update[O](k: Key[O], v: O): Task[Unit] =
        topics.get(k).map(_.current.set(v)).getOrElse(Task(())(defaultPool)).map(_ => ())

      def get[O](k: Key[O]): Signal[O] =
        topics.get(k).map(_.current.asInstanceOf[Signal[O]])
                     .getOrElse(sys.error("key not found: " + k))

      def remove[O](k: Key[O]): Task[Unit] = for {
        _ <- keys_.compareAndSet(_.map(_ - k))
        _ <- topics.get(k).traverse_(_.current.close)
        _ <- Task.delay(topics -= k)
      } yield ()

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
  implicit ES: ExecutorService = serverPool):
      Process[Task, Datapoint[Any]] = {
   def interleaveAll(p: Key[Any] => Boolean): Process[Task, Datapoint[Any]] =
     scalaz.stream.merge.mergeN(M.distinctKeys.filter(p).map(k => points(k)))
   def points(k: Key[Any]): Process[Task, Datapoint[Any]] =
     M.get(k).discrete.map(Datapoint(k, _)).onComplete {
       Process.eval_(Task.delay(M.log.debug(s"unsubscribing: $k")))
     }
   interleaveAll(f)
  }

  /**
   * Obtain the latest values for all active metrics.
   */
  def snapshot(M: Monitoring)(implicit ES: ExecutorService = defaultPool):
    Task[collection.Map[Key[Any], Datapoint[Any]]] = {
    val m = collection.concurrent.TrieMap[Key[Any], Datapoint[Any]]()
    implicit val S = Strategy.Executor(ES)
    for {
      ks <- M.keys.compareAndSet(identity).map(_.getOrElse(Set.empty))
      t <- Nondeterminism[Task].gatherUnordered {
        ks.map(k => M.get(k).compareAndSet(identity).map(
                 _.map(v => k -> Datapoint(k, v))
               ).attempt.map(_.toOption)).toSeq
      }.map(_.toSet)
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
      Task[(I => Task[Unit], Signal[O])] = Task.delay {

    val S = Strategy.Executor(ES)
    val signal = signalUnset[O](Strategy.Sequential)

    var cur = buf.unemit match {
      case (h, t) if h.nonEmpty => signal.set(h.last).run; t
      case (h, t) => t
    }

    val hub = Actor.actor[I] { i =>
      val (h, t) = process1.feed1(i)(cur).unemit
      if (h.nonEmpty) signal.set(h.last).run
      cur = t
      cur match {
        case Process.Halt(e) => signal.fail(e.asThrowable).run
        case _ => ()
      }
    }(S)
    ((i: I) => Task.delay(hub ! i), signal)
  }

  /**
   * Try running the given process `p`, catching errors and reporting
   * them with `maskedError`, using `schedule` to determine when further
   * attempts are made. If `schedule` is exhausted, the error is raised.
   * Example: `attemptRepeatedly(println)(p)(Process.awakeEvery(10 seconds).take(3))`
   * will run `p`; if it encounters an error, it will print the error using `println`,
   * then wait 10 seconds and try again. After 3 reattempts it will give up and raise
   * the error in the `Process`.
   */
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

  private[funnel] def formatURI(uri: URI): String = {
    val host = uri.getHost
    val path = uri.getPath
    val port = uri.getPort match {
      case -1 => ""
      case x => "-"+x
    }
    host + port + path
  }
}

