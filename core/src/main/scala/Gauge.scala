//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel

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
    val b = new Gauge.Buffer[Option[A]](d, None)(
      append = (_, a) => a, //ensures only the most recently appended value is sent
      reset = a => a,
      publish = a => self.set(a.get))
    def set(a: A): Unit = b(Some(a))
    def keys = self.keys
  }

  def map[B](f: B => A): Gauge[K, B] = new Gauge[K, B] {
    def set(b: B): Unit = self.set(f(b))
    def keys = self.keys
  }
}

object Gauge {

  class Buffer[A](d: Duration, init: A)(append: (A, A) => A, reset: A => A, publish: A => Unit)(
    implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
    S2: ExecutorService = Monitoring.defaultPool) { self =>
      if (d < 100.microseconds)
        sys.error("buffer size must be at least 100 microseconds, was: " + d)

      var delta = init
      var scheduled = false
      val nanos = d.toNanos
      val later = Strategy.Executor(S2)

      val send: Actor[Option[A]] = actor[Option[A]] { msg =>
        msg.map { a =>
          delta = append(delta, a)
          if (!scheduled) {
            scheduled = true
            S.schedule(task, nanos, TimeUnit.NANOSECONDS)
          }
        } getOrElse {
          scheduled = false
          publish(delta)
          delta = reset(delta)
        }; ()
      }(later)

      val task = new Runnable {
        def run = {
          self.send(None) //flush the buffer
        }
      }

      def apply(a: A) = send(Some(a))
    }

  def scale[K](k: Double)(g: Gauge[K,Double]): Gauge[K,Double] =
    g map (_ * k)

  implicit def contravariantGauge[K]: Contravariant[({type λ[α] = Gauge[K,α]})#λ] =
    new Contravariant[({type λ[α] = Gauge[K, α]})#λ] {
      def contramap[A,B](ga: Gauge[K, A])(f: B => A) = ga map f
    }
}
