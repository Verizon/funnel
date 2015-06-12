package funnel
package chemist

/**
 * Given locations are modeling where a monitorable thing resides,
 * and it `Location` is somewhat overloaded as its used for both
 * admin channels and mirroring channels, `LocationIntent` serves
 * as a discriminator for sequences of Locations.
 */
sealed trait LocationIntent

object LocationIntent {
  def fromString(s: String): Option[LocationIntent] =
    all.find(_.toString.toLowerCase == s.toLowerCase)

  lazy val all = List(Mirroring, Supervision)

  case object Mirroring extends LocationIntent
  case object Supervision extends LocationIntent
}
