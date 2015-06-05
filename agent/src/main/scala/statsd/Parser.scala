package funnel
package agent
package statsd

import scala.math.round
import util.matching.Regex
import scalaz.\/

object Parser {

  // borrowed this from the bsd work here:
  // https://github.com/mojodna/metricsd
  private[statsd] val matcher =
    new Regex("""([^:]+)(:((-?\d+(?:\.?\d*)|delete)?(\|((\w+)(\|@(\d+\.\d+))?)?)?)?)?""")

  def toRequest(line: String): String => Throwable \/ InstrumentRequest =
    cluster => toMetric(line).map(InstrumentRequest(cluster, _))

  def toMetric(line: String): Throwable \/ ArbitraryMetric =
    for {
      a <- fromString(line)
      b <- toInstrumentKind(a._3)
      c <- toSampleRate(a._4)
      d <- toValue(a._2, c)
    } yield ArbitraryMetric(a._1,b,Option(d.toString))

  private[statsd] def fromString(line: String): Throwable \/ (String,String,String,String) = {
    \/.fromTryCatchNonFatal {
      val matcher(name,_,_,value,_,_,kind,_, sampleRate) = line
      (name, value, kind, sampleRate)
    }.leftMap(err => new RuntimeException(s"Unable to parse input. Check the formating and ensure you are using valid statsd syntax. Error was: $err"))
  }

  private[statsd] def toInstrumentKind(s: String): Throwable \/ InstrumentKind = {
    s.trim match {
      case "c"  => \/.right(InstrumentKinds.Counter)
      case "ms" => \/.right(InstrumentKinds.Timer)
      case "g"  => \/.right(InstrumentKinds.GaugeDouble)
      case "m"  => \/.right(InstrumentKinds.Counter)
      case _   => \/.left(new Exception("String did not convert to a valid instrument kind."))
    }
  }

  private[statsd] def toValue(s: String, rate: Double): Throwable \/ Long = {
    for {
      a <- \/.fromTryCatchNonFatal(s.trim.toLowerCase)
      _ <- if(a == "delete") \/.left(new Exception("Deletion is not supported.")) else \/.right(a)
      b <- \/.fromTryCatchNonFatal(a.toDouble)
    } yield round(b * 1 / rate)
  }

  private[statsd] def toSampleRate(s: String): Throwable \/ Double =
    Option(s).map(_.toDouble).map(\/.right(_))
      .getOrElse(\/.right(1.0))

}