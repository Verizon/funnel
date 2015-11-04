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

import com.twitter.algebird.Group
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.Strategy

/**
 * A gauge whose readout value type is characterized by a `Group`.
 *
 * See http://en.wikipedia.org/wiki/Group_%28mathematics%29
 */
abstract class PeriodicGauge[A](implicit A: Group[A]) extends Instrument[Periodic[A]] { self =>
  def append(a: A): Unit
  final def remove(a: A): Unit =
    append(A.negate(a))

  /**
   * Delay publishing updates to this `GroupGauge` for the
   * given duration after modification.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): PeriodicGauge[A] =
    new PeriodicGauge[A] {
      val b = new Gauge.Buffer(d, A.zero)(A.plus, _ => A.zero, self.append)
      def append(a: A) = b(a)
      def keys = self.keys
    }
}

