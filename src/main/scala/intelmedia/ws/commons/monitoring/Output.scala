package intelmedia.ws.commons.monitoring

import java.util.concurrent.ExecutorService
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream._
import Pru._
import Writer._

object Output {

  import Reportable._

  def toJSON(k: Key[Any]): Action =
    JSON.obj("label" -> literal(k.label), "id" -> literal(k.id.toString))

  def toJSON(ks: Seq[Key[Any]]): Action =
    JSON.list(ks.map(toJSON): _*)

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
    case Stats(a) => JSON.obj(
      "kind" -> literal("Stats"),
      "count" -> k(a.count.toString),
      "mean" -> toJSON(a.mean),
      "variance" -> toJSON(a.variance),
      "skewness" -> toJSON(a.skewness),
      "kurtosis" -> toJSON(a.kurtosis))
    case _ => sys.error("unrecognized reportable: " + r)
  }

  def toJSON(m: Traversable[(Key[Any],Reportable[Any])]): Action =
    JSON.list { m.toList.map { case (key, v) =>
      JSON.obj("label" -> literal(key.label),
          "id" -> literal(key.id.toString),
          "value" -> toJSON(v))
    }: _*}

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

// todo - properly publish this
object Pru {
  import scala.xml.Utility.{escape => esc}

  case class Action(f: StringBuilder => Unit) extends (StringBuilder => Unit) {
    def ++(other: Action): Action =
      Action(sb => { apply(sb); other(sb) })
    def apply(sb: StringBuilder): Unit = f(sb)

    override def toString: String =
      { val sb = new StringBuilder; apply(sb); sb.toString }
  }

  object Writer {
    type Writer[A] = A => Action

    implicit def toAction(f: StringBuilder => Unit): Action = Action(f)

    def any(a: Any): Action = Action(sb => sb append a.toString)
    def k(s: String): Action = Action(sb => sb append s)
    def escape(s: String): Action = Action(sb => sb append esc(s))
    def literal(s: String): Action = k("\"") ++ escape(s) ++ k("\"")
    def id: Action = Action(sb => ())
    def rep[A](f: Writer[A]): Writer[Traversable[A]] = as => Action(sb => as.foreach(x => f(x)(sb)))
    def rep[A](t: Traversable[A])(f: Writer[A]): Action = rep(f)(t)
    def concat(as: Traversable[Action]): Action = Action(sb => as.foreach(f => f(sb)))
    def concat(a: Action, as: Action*): Action = concat(a +: as)
    def intersperse(a: Action)(as: Action*) = Action { sb =>
      if (as.isEmpty) ()
      else if (as.tail.isEmpty) as.head(sb)
      else { as.head(sb); as.tail.foreach { e => a(sb); e(sb) }}
    }
  }

  object XML {
    import Writer._

    def attributes(attrs: (String, String)*): Action =
      Action(sb => attrs.foreach { case (x, y) => sb append (" " + x + "=\"" + escape(y) + "\"") })

    def openTag(tag: String, attrs: (String, String)*): Action =
      k("<") ++ k(tag) ++ attributes(attrs: _*) ++ k(">")

    def closeTag(tag: String): Action = k ("</" + tag + ">")

    def leafTag(tag: String, attrs: (String, String)*): Action =
      k("<") ++ k(tag) ++ attributes(attrs: _*) ++ k("/>")

    def nest[A](tag: String, attributes: (String, String)*)(inner: Action*): Action =
      openTag(tag, attributes: _*) ++ concat(inner) ++ closeTag(tag)

    def nest[A](tag: String)(inner: Action*): Action = nest(tag, Seq(): _*)(inner: _*)
  }

  object JSON {
    def entry(key: String, value: Action): Action =
      literal(key) ++ k(" : ") ++ value
    def obj(entries: (String, Action)*): Action =
      k("{ ") ++ intersperse(k(", "))(entries.view.map(p => entry(p._1, p._2)): _*) ++ k(" }")
    def list(values: Action*): Action =
      k("[") ++ intersperse(k(",\n"))(values: _*) ++ k("]")
  }

}
