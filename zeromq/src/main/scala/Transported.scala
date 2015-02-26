package funnel
package zeromq

case class Transported(
  scheme: Scheme,
  version: Version,
  topic: Option[Topic],
  bytes: Array[Byte]
) {
  def header: String =
    topic.fold(s"$scheme/$version")(topic => s"$scheme/$version/$topic")
}

object Transported {
  import Versions._

  def apply(h: String, bytes: Array[Byte]): Transported = {
    val a = h.split('/')
    val v = (if(a.length > 1) Versions.fromString(a(1)) else None).getOrElse(Versions.unknown)
    val topic = if(a.length > 2) Some(Topic(a(2))) else None

    Transported(Schemes.fromString(a(0)), v, topic, bytes)
  }
}

abstract class Transportable[A] {
  def apply(a: A): Transported
}

object Transportable {
  def apply[A](f: A => Transported): Transportable[A] = new Transportable[A] { def apply(a: A) = f(a) }
}
