package intelmedia.ws.commons.monitoring

import java.util.concurrent.{Executors, ExecutorService, ThreadFactory}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scalaz.concurrent.{Actor,Strategy,Task}
import scalaz.Nondeterminism
import scalaz.stream._
import scalaz.stream.async

/**
 * TODO: document me
 */
trait Monitoring {
  import Monitoring._

  /** Create a new topic with the given label. */
  def topic[I, O <% Reportable[O]](
    label: String)(
    buf: Process1[(I,Duration),O]): (Key[O], I => Unit)

  // todo: docs, mention topic vs signal semantics
  def get[O](k: Key[O]): async.immutable.Signal[Reportable[O]]

  // def publish[O <% Reportable[O]](label: String)(k: Key[O]): Key[O]

  /**
   * Return the most recent value for a given key.
   */
  def latest[O](k: Key[O]): Task[O] =
    get(k).continuous.once.runLast.map(_.get.get)

  /** The time-varying set of keys. */
  def keys: async.immutable.Signal[List[Key[Any]]]

  /** Create a new topic with the given label and discard the key. */
  def topic_[I, O <% Reportable[O]](
    label: String)(
    buf: Process1[(I,Duration),O]): I => Unit = topic(label)(buf)._2

  def keysByLabel(label: String): Process[Task, List[Key[Any]]] =
    keys.continuous.map(_.filter(_.label == label))
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

  val default: Monitoring = instance(defaultPool)

  def instance(implicit ES: ExecutorService = defaultPool): Monitoring = {
    import async.immutable.Signal
    val t0 = System.nanoTime
    val S = Strategy.Executor(ES)
    val P = Process
    val keys_ = async.signal[List[Key[Any]]](S)
    keys_.value.set(List())

    case class Topic[I,O](
      publish: ((I,Duration), Option[Reportable[O]] => Unit) => Unit,
      current: async.immutable.Signal[Reportable[O]]
    )
    var topics = new collection.concurrent.TrieMap[Key[Any], Topic[Any,Any]]()

    def eraseTopic[I,O](t: Topic[I,O]): Topic[Any,Any] = t.asInstanceOf[Topic[Any,Any]]

    new Monitoring {
      def keys = keys_

      def topic[I, O <% Reportable[O]](
          label: String)(
          buf: Process1[(I,Duration),O]): (Key[O], I => Unit) = {
        val (pub, v) = bufferedSignal(buf.map(Reportable.apply(_)))(ES)
        val k = Key[O](label)
        topics += (k -> eraseTopic(Topic(pub, v)))
        keys_.value.modify(k :: _)
        (k, (i: I) => {
          val elapsed = Duration.fromNanos(System.nanoTime - t0)
          pub(i -> elapsed, _ => {})
        })
      }

      def get[O](k: Key[O]): Signal[Reportable[O]] =
        topics.get(k).map(_.current.asInstanceOf[Signal[Reportable[O]]])
                     .getOrElse(sys.error("key not found: " + k))
    }
  }

  /**
   * Obtain the latest values for all active metrics.
   */
  def snapshot(M: Monitoring)(implicit ES: ExecutorService = defaultPool):
    Task[collection.Map[Key[Any], Reportable[Any]]] = {
    val m = collection.concurrent.TrieMap[Key[Any], Reportable[Any]]()
    val S = Strategy.Executor(ES)
    for {
      ks <- M.keys.continuous.once.runLastOr(List())
      t <- Nondeterminism[Task].gatherUnordered {
        ks.map(k => M.get(k).continuous.once.runLast.map(
          _.map((k, _))
        ).timed(100L).attempt.map(_.toOption))
      }
      _ <- Task { t.flatten.flatten.foreach(m += _) }
    } yield m
  }

  /**
   * Send values through a `Process1[I,O]` to a `Signal[O]`, which will
   * always be equal to the most recent value produced by `buf`. Sending
   * `None` to the returned `Option[I] => Unit` closes the `Signal`.
   * Sending `Some(i)` updates the value of the `Signal`, after passing
   * `i` through `buf`.
   */
  private[monitoring] def bufferedSignal[I,O](
      buf: Process1[I,O])(
      implicit ES: ExecutorService = defaultPool):
      ((I, Option[O] => Unit) => Unit, async.immutable.Signal[O]) = {
    val signal = async.signal[O](Strategy.Executor(ES))
    var cur = buf.unemit match {
      case (h, t) if h.nonEmpty => signal.value.set(h.last); t
      case (h, t) => t
    }
    val hub = Actor.actor[(I, Option[O] => Unit)] { case (i,done) =>
      val (h, t) = process1.feed1(i)(cur).unemit
      if (h.nonEmpty) {
        val out = Some(h.last)
        signal.value.compareAndSet(_ => out, _ => done(out))
      }
      else done(None)
      cur = t
      cur match {
        case Process.Halt(e) => signal.value.fail(e)
        case _ => ()
      }
    } (Strategy.Sequential)
    ((i: I, done: Option[O] => Unit) => hub ! (i -> done), signal)
  }

}

