package intelmedia.ws.commons.monitoring

import Pru._
import Writer._

object Output {

  import Reportable._

  def toJSON[A](r: Reportable[A]): Action = r match {
    case I(a) => k(a.toString)
    case B(a) => k(a.toString)
    case D(a) => k(a.toString)
    case S(s) => literal(s)
    case Stats(a) => JSON.obj(
      "kind" -> literal("Stats"),
      "count" -> k(a.count.toString),
      "mean" -> k(a.mean.toString),
      "variance" -> k(a.variance.toString),
      "skewness" -> k(a.skewness.toString),
      "kurtosis" -> k(a.kurtosis.toString))
    case _ => sys.error("unrecognized reportable: " + r)
  }

  def toJSON(m: Traversable[(Key[Any],Reportable[Any])]): Action =
    JSON.list { m.toList.map { case (key, v) =>
      JSON.obj("label" -> literal(key.label),
          "id" -> literal(key.id.toString),
          "value" -> toJSON(v))
    }: _*}

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
