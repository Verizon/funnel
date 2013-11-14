package intelmedia.ws
package monitoring

import java.util.concurrent.TimeUnit

sealed trait Units[+A]

object Units {
  case class Duration(granularity: TimeUnit) extends Units[Double with monitoring.Stats]
  case class Bytes(granularity: Base) extends Units[Double with monitoring.Stats]
  case object Count extends Units[Double with monitoring.Stats]
  case object Ratio extends Units[Double with monitoring.Stats]
  case object Stoplight extends Units[String] // "red", "yellow", "green"
  case object Load extends Units[String] // "idle", "busy", "overloaded"

  case object Dimensionless extends Units[Nothing]

  val Milliseconds = Duration(TimeUnit.MILLISECONDS)
  val Megabytes = Bytes(Base.Mega)

  trait Base
  object Base {
    case object One extends Base
    case object Kilo extends Base
    case object Mega extends Base
    case object Giga extends Base
  }
}
