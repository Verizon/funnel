package oncue.svc.funnel
package agent
package statsd

// import scala.math.round
// import java.util.concurrent.TimeUnit
import util.matching.Regex
import scalaz.\/

object Parser {

  private[statsd] val matcher =
    new Regex("""([^:]+)(:((-?\d+|delete)?(\|((\w+)(\|@(\d+\.\d+))?)?)?)?)?""")

  def parse(line: String): String => Throwable \/ InstrumentRequest = { cluster =>
    for {
      a <- \/.fromTryCatchThrowable[(String,String,String,String), Throwable] {
        val matcher(name,_,_,value,_,_,kind,_, sampleRate) = line
        (name, value, kind, sampleRate)
      }
      b <- toInstrumentKind(a._3)
      c <- toSampleRate(a._4)
      d <- toValue(a._2)
    } yield InstrumentRequest(cluster, ArbitraryMetric(a._1,b,Option(d)))
  }

  private def toInstrumentKind(s: String): Throwable \/ InstrumentKind = {
    s.trim match {
      case "c"  => \/.right(InstrumentKinds.Counter)
      case "ms" => \/.right(InstrumentKinds.Timer)
      case "g"  => \/.right(InstrumentKinds.GaugeDouble)
      case "m"  => \/.right(InstrumentKinds.Counter)
      case _   => \/.left(new Exception("String did not convert to a valid instrument kind."))
    }
  }

  private def toValue(s: String): Throwable \/ String =
    if(s.trim.toLowerCase == "delete") \/.left(new Exception("Deletion is not supported."))
    else \/.right(s.trim)

  private def toSampleRate(s: String): Throwable \/ Double =
    Option(s.toDouble).map(\/.right(_))
      .getOrElse(\/.right(1.0))

  // msg.trim.split("\n").foreach {

  // }

}