package intelmedia.ws.monitoring

import java.util.concurrent.ExecutorService
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream._
import pru.write.Writer._
import pru.write.{Action,JSON}

object Output {

  import Reportable._
  import JSON.literal

  def toJSON(k: Key[Any]): Action =
    JSON.Obj("label" -> literal(k.label), "id" -> literal(k.id.toString))

  def toJSON(ks: Seq[Key[Any]]): Action =
    JSON.list(ks.map(toJSON))

  def toJSON(d: Double): Action =
    if (d.isNaN ||
        d == Double.PositiveInfinity ||
        d == Double.NegativeInfinity)
      k("null")
    else k(d.toString)

  def toJSON[A](r: Reportable[A]): Action = r match {
    case I(a) => k(a.toString)
    case B(a) => k(a.toString)
    case D(a) => k(a.toString)
    case S(s) => literal(s)
    case Stats(a) => JSON.Obj(
      "kind" -> literal("Stats"),
      "count" -> k(a.count.toString),
      "last" -> a.last.map(any).getOrElse(k("null")),
      "mean" -> toJSON(a.mean),
      "variance" -> toJSON(a.variance),
      "skewness" -> toJSON(a.skewness),
      "kurtosis" -> toJSON(a.kurtosis))
    case _ => sys.error("unrecognized reportable: " + r)
  }

  def toJSON(m: Traversable[(Key[Any],Reportable[Any])]): Action =
    JSON.list { m.toList.map { case (key, v) =>
      JSON.Obj("label" -> literal(key.label),
          "id" -> literal(key.id.toString),
          "value" -> toJSON(v))
    }}

  /** Format a duration like `62 seconds` as `0hr 1m 02s` */
  def hoursMinutesSeconds(d: Duration): String = {
    val hours = d.toHours
    val minutes = ((d.toMinutes minutes)- (hours hours)).toMinutes
    val seconds = ((d.toSeconds seconds) - (hours hours) - (minutes minutes)).toSeconds
    s"${hours}hr ${minutes}m ${seconds}s"
  }

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given metrics to the `Writer`. This will block the calling
   * thread indefinitely.
   */
  def eventsToSSE(events: Process[Task, (Key[Any], Reportable[Any])],
                  sink: java.io.Writer): Unit =
    events.map(kv => s"event: ${toJSON(kv._1)}\ndata: ${toJSON(kv._2)}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given keys to the given `Writer`. This will block the calling
   * thread indefinitely.
   */
  def keysToSSE(events: Process[Task, Key[Any]], sink: java.io.Writer): Unit =
    events.map(k => s"event: key\ndata: ${toJSON(k)}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  private def writeTo(sink: java.io.Writer): String => Unit =
    line => try {
      sink.write(line)
      sink.flush // this is a line-oriented protocol,
                 // so we flush after each line, otherwise
                 // consumer may get delayed messages
    }
    catch { case e: java.io.IOException =>
      // when client disconnects we'll get a broken pipe
      // IOException from the above `sink.write`. This
      // gets translated to normal termination
      throw Process.End
    }
}
