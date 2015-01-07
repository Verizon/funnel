package oncue.svc.funnel.agent

import oncue.svc.funnel.{Units,Instrument,Instruments,Reportable,Counter,Timer,Periodic,Stats}
import scalaz.concurrent.Task
import java.util.concurrent.ConcurrentHashMap
import scalaz.\/

// trait InstrumentBuilder[A]{ // fucking terrible name.
//   // def apply(in: ArbitraryMetric): A
//   def filter: InstrumentKind => Boolean
// }
// object InstrumentBuilder {
//   import InstrumentKinds._

//   implicit def counterBuilder: InstrumentBuilder[Counter[Periodic[Double]]] =
//     new InstrumentBuilder[Counter[Periodic[Double]]]{
//       // def apply(a: ArbitraryMetric): Counter[Periodic[Double]] = Operations.loadCounter(a.name)(I)
//       val filter: InstrumentKind => Boolean = _ == Counter
//     }

//   implicit def timerBuilder(implicit I: Instruments): InstrumentBuilder[Timer[Periodic[Stats]]] =
//     new InstrumentBuilder[Timer[Periodic[Stats]]]{
//       // def apply(a: ArbitraryMetric): Timer[Periodic[Stats]] = Operations.loadTimer(a.name)(I)
//       val filter: InstrumentKind => Boolean = _ == Timer
//     }
// }

object Operations {
  import collection.JavaConverters._
  import scala.reflect.runtime.universe._

  type MetricName = String

  private val counters = new ConcurrentHashMap[MetricName, Counter[Periodic[Double]]]
  private val timers   = new ConcurrentHashMap[MetricName, Timer[Periodic[Stats]]]

  private def lookup[A](key: MetricName)(hash: ConcurrentHashMap[MetricName, A]): Option[A] =
    Option(hash.get(key))

  def loadCounter(m: ArbitraryMetric)(I: Instruments): Counter[Periodic[Double]] =
    lookup[Counter[Periodic[Double]]](m.name)(counters).getOrElse {
      val c = I.counter(m.name)
      counters.putIfAbsent(m.name, c)
      c
    }

  def loadTimer(m: ArbitraryMetric)(I: Instruments): Timer[Periodic[Stats]] =
    lookup[Timer[Periodic[Stats]]](m.name)(timers).getOrElse {
      val t = I.timer(m.name)
      timers.putIfAbsent(m.name, t)
      t
    }
}
