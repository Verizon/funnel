package funnel

import scala.concurrent.duration._
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.tuple._

import Events._

trait Metrics {
  /** Infix syntax for `Metric`. */
  implicit class MetricOps[A](self: Metric[A]) {

    def eval(M: Monitoring = Monitoring.default): (Event, OneOrThree[Task[A]]) =
      self.foldMap[λ[α => (Event,OneOrThree[Task[α]])]](new (KeySet ~> λ[α => (Event,OneOrThree[Task[α]])]) {
        def apply[A](a: KeySet[A]) = a match {
          case One(k) => (changed(k), One(M latest k))
          case Three(now, prev, sliding) =>
            (or(changed(now), or(changed(prev),changed(sliding))),
             Three(M latest now, M latest prev, M latest sliding))
        }
      })(tuple2Monad[Event] compose Applicative[OneOrThree] compose Applicative[Task])

    def publish(label: String,
                units: Units = Units.None,
                description: String = "",
                M: Monitoring = Monitoring.default,
                I: Instruments = instruments.instance,
                keyMod: Key[A] => Key[A] = identity[Key[A]] _)(
                implicit R: Reportable[A]): Task[KeySet[A]] = {
      val keys = I.periodicKeys(label, units, description, keyMod)
      val (ev, oot) = eval(M)
      oot match {
        case One(t) => M.publish(keys.now)(ev)(t).map(One(_))
        case Three(now, prev, sliding) => for {
          n <- M.publish(keys.now)(ev)(now)
          p <- M.publish(keys.previous)(ev)(prev)
          s <- M.publish(keys.sliding)(ev)(sliding)
        } yield Three(n, p, s)
      }
    }
  }
}
