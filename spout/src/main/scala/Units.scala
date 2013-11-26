package intelmedia.ws
package monitoring

import java.util.concurrent.TimeUnit

sealed trait Units[+A]

object Units {

  // NB: Duration, Bytes, Count, Ratio can be units associated
  // with a Stats metric, or a single-value metric, hence
  // the intersection type

  case class Duration(granularity: TimeUnit) extends Units[Double with monitoring.Stats]
  case class Bytes(base: Base) extends Units[Double with monitoring.Stats]
  case object Count extends Units[Double with monitoring.Stats]
  case object Ratio extends Units[Double with monitoring.Stats]
  case object Stoplight extends Units[String] // "red", "yellow", "green"
  case object Healthy extends Units[Boolean]
  case object Load extends Units[String] // "idle", "busy", "overloaded"

  case object None extends Units[Nothing]

  val Milliseconds = Duration(TimeUnit.MILLISECONDS)
  val Seconds = Duration(TimeUnit.SECONDS)
  val Minutes = Duration(TimeUnit.MINUTES)
  val Hours = Duration(TimeUnit.HOURS)
  val Megabytes = Bytes(Base.Mega)

  sealed trait Base
  object Base {
    case object Zero extends Base // 10^0 = 1
    case object Kilo extends Base // 10^3
    case object Mega extends Base // 10^6
    case object Giga extends Base // 10^9
  }
}
