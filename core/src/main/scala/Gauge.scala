package oncue.svc.funnel

import java.util.concurrent.atomic._
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.Contravariant
import scalaz.concurrent.Actor
import scalaz.concurrent.Actor._

trait Gauge[K,A] extends Instrument[K] { self =>

  def set(a: A): Unit

  /**
   * Delay publishing updates to this gauge for the
   * given duration after a call to `set`. If multiple
   * values are `set` within the timing window, only the
   * most recent value is published.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Gauge[K, A] = new Gauge[K, A] {
    val b = Gauge.buffer[Option[A]](d, None)((_, a) => a, a => a, a => set(a.get))
    def set(a: A): Unit = b(Some(a))
    def keys = self.keys
  }

  def map[B](f: B => A): Gauge[K, B] = new Gauge[K, B] {
    def set(b: B): Unit = self.set(f(b))
    def keys = self.keys
  }
}

object Gauge {

  def buffer[A](d: Duration, init: A)(append: (A, A) => A, reset: A => A, k: A => Unit)(
    implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
    S2: ExecutorService = Monitoring.defaultPool): A => Unit = {
      if (d < (100 microseconds))
        sys.error("buffer size be at least 100 microseconds, was: " + d)

      var delta = init
      var scheduled = false
      val nanos = d.toNanos
      val later = Strategy.Executor(S2)

      lazy val send: Actor[Option[A]] = actor[Option[A]] { msg =>
        msg.map { a =>
          delta = append(delta, a)
          if (!scheduled) {
            scheduled = true
            val task = new Runnable {
              def run = send(None)
            }
            S.schedule(task, nanos, TimeUnit.NANOSECONDS)
          }
        } getOrElse {
          scheduled = false
          k(delta)
          delta = reset(delta)
        }
      }(later)

      a => send(Some(a))
    }

  def scale[K](k: Double)(g: Gauge[K,Double]): Gauge[K,Double] =
    g map (_ * k)

  implicit def contravariantGauge[K]: Contravariant[({type λ[α] = Gauge[K,α]})#λ] =
    new Contravariant[({type λ[α] = Gauge[K, α]})#λ] {
      def contramap[A,B](ga: Gauge[K, A])(f: B => A) = ga map f
    }
}
