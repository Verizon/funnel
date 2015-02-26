package funnel
package zeromq

/**
 * A payload for transmitting via a ∅ socket
 */
case class Transported(
  /** the scheme by which this packet was encoded */
  scheme: Scheme,
  /** The version of the scheme */
  version: Version,
  /** An optional "topic" for this packet, which can be used for filtering subscriptions */
  topic: Option[Topic],
  /** The actual payload */
  bytes: Array[Byte]
) {
  /**
   * construct the String for the header packet
   */
  def header: String =
    topic.fold(s"$scheme/$version")(t => s"$scheme/$version/$t")
}

object Transported {
  import Versions._

  /**
   * Reconstructed the Transported instance on the receive side given the header and payload
   */
  def apply(h: String, bytes: Array[Byte]): Transported = {
    val a = h.split('/')
    val v = (if(a.length > 1) Versions.fromString(a(1)) else None).getOrElse(Versions.unknown)
    val topic = if(a.length > 2)
      Some(Topic(a(2)))
    else if(h.endsWith("/"))
      Some(Topic(""))
    else
      None

    Transported(Schemes.fromString(a(0)), v, topic, bytes)
  }
}

/**
 * A typeclass for preparing an A for being written to a ∅ socket, It
 * needs to be able to produce a Transportable object for any instance
 * of A
 */
abstract class Transportable[A] {
  def apply(a: A): Transported
}

object Transportable {
  // create a Transportable from a A => Transported
  def apply[A](f: A => Transported): Transportable[A] = new Transportable[A] { def apply(a: A) = f(a) }
}
