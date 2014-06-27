package intelmedia.ws
package funnel

import scalaz.\/
import scalaz.\/.{left,right}
import scalaz.concurrent.{Actor, Task, Strategy}
import scalaz.stream.async.mutable.Signal
import scalaz.stream.{Process,Sink}

/**
 * Extends `mutable.Signal` with a `sample` function.
 */
trait SampledSignal[A] extends Signal[A] {

  /**
   * Immediately returns current value of the `Signal`,
   * or `None` if the `Signal` is unset.
   */
  def sample: Task[Option[A]]
}

object SampledSignal {

  def apply[A](implicit S: Strategy): SampledSignal[A] = {
    var value: Throwable \/ Option[A] = right(None)
    var listeners = Vector.empty[Throwable \/ A => Unit]

    trait M // message type for our actor
    case class Fail(err: Throwable) extends M
    case class Modify(f: Option[A] => Option[A], cb: Throwable \/ Option[A] => Unit) extends M
    case class Discrete(cb: Throwable \/ A => Unit) extends M
    case class Get(cb: Throwable \/ A => Unit) extends M
    case class Sample(cb: Throwable \/ Option[A] => Unit) extends M

    val actor = Actor.actor[M] {
      case Fail(err) => // terminate the signal
        val v = left(err)
        value = v
        listeners.foreach { cb => cb(v) }
        listeners = Vector.empty
      case Discrete(cb) => // wait for next update
        value.fold(
          err => cb(left(err)), // fail fast if signal has been killed
          oa => listeners = listeners :+ cb // otherwise add ourselves to queue
        )
      case Get(cb) => // like discrete, except if value is Some, invoke `cb` immediately
        value.fold(
          err => cb(left(err)),
          oa => oa match {
            case None => listeners = listeners :+ cb // signal not set, enqueue as in `Discrete`
            case Some(a) => cb(right(a)) // signal already set, invoke cb immediately
          }
        )
      case Sample(cb) =>
        cb(value) // invoke `cb` with current value, even if `None`
      case Modify(f, cb) => // modify the current value and invoke `cb` when done
        value.fold(
          err => cb(left(err)), // fail immediately
          oa => {
            val oa2 = f(oa)
            value = right(oa2)
            // if value is now set, notify any discrete listeners
            oa2.foreach { a =>
              listeners.foreach { cb => cb(right(a)) }
              listeners = Vector.empty
            }
            cb(value)
          }
        )
    }

    new SampledSignal[A] {
      def sample: Task[Option[A]] =
        Task.async[Option[A]] { cb => actor ! Sample(cb) }
      def sink: Sink[Task, Signal.Msg[A]] =
        Process.constant[Signal.Msg[A] => Task[Unit]] { msg => msg match {
          case Signal.Set(a) => set(a)
          case Signal.CompareAndSet(f) =>
            // scala pattern match fail, gets confused about type of f, despite
            // case class CompareAndSet[A](f:Option[A] => Option[A]) extends Msg[A]
            compareAndSet(f.asInstanceOf[Option[A] => Option[A]]).map(_ => ())
          case Signal.Fail(err) => fail(err)
        }}
      def get: Task[A] = Task.async[A] { cb => actor ! Get(cb) }
      def set(a: A): Task[Unit] =
        Task.async[Option[A]] { cb => actor ! Modify(_ => Some(a), cb) }
            .map(_ => ())
      def getAndSet(a: A): Task[Option[A]] = Task.suspend {
        // somewhat hideous, we use a var to cache the previous
        // value when the modification message is processed
        @volatile var prev: Option[A] = None
        Task.async[Option[A]] { cb =>
          actor ! Modify(oa => { prev = oa; Some(a) },
                         e => e.fold(err => cb(left(err)),
                                     _ => cb(right(prev))))
        }
      }
      def compareAndSet(f: Option[A] => Option[A]): Task[Option[A]] =
        Task.async[Option[A]] { cb => actor ! Modify(f, cb) }
      def fail(error: Throwable): Task[Unit] = Task.delay { actor ! Fail(error) }
      def changed: Process[Task,Boolean] = changes.map(_ => true) merge Process.constant(false)
      def changes: Process[Task,Unit] = discrete.map(_ => ())
      def continuous: Process[Task,A] = Process.repeatEval(get)
      def discrete: Process[Task,A] = {
        val d = Task.async[A] { cb => actor ! Discrete(cb) }
        Process.repeatEval(d)
      }
    }
  }
}

