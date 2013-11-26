package intelmedia.ws.monitoring

import scalaz.{~>, Monad}
import scala.language.higherKinds

class Key[+A] private[monitoring](val name: String, val id: java.util.UUID) {
  override def equals(a: Any): Boolean =
    a.asInstanceOf[Key[Any]].id == id
  override def hashCode = id.hashCode
  override def toString = s"Key($name)"
  def matches(prefix: String): Boolean =
    name.startsWith(prefix) || id.toString.startsWith(prefix)
  def rename(s: String) = new Key[A](s, id)
}

object Key {
  private[monitoring] def apply[A](label: String): Key[A] =
    new Key(label, java.util.UUID.randomUUID)

  implicit def keyToMetric[A](k: Key[A]): Metric[A] = Metric.key(k)
}
