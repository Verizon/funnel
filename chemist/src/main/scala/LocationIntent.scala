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

  /**
   * Make a best effort to convert a string into a `LocationIntent`.
   * All strings are lowercased and trimmed:
   * `mirroring`, `supervision`, `ignored`
   */
  def fromString(s: String): Option[LocationIntent] =
    all.find(_.toString.toLowerCase == s.toLowerCase)

  lazy val all = List(Mirroring, Supervision)

  /**
   * locations destined for mirroring, should be
   * accessible for Flask to come and collect metrics on.
   * The protocol should be determined by the templates.
   */
  case object Mirroring extends LocationIntent

  /**
   * location destined for supervision are only for use
   * between flask and chemist components. Supervision
   * locations provide a channel for the administrative
   * state and control messages chemist needs to operate.
   */
  case object Supervision extends LocationIntent

  /**
   * locations that are meant to be ignored are few and far
   * between, but this is supplied for completeness. One
   * case where `Ignored` is useful is when running multiple
   * clusters of flasks during a migration from one cluster
   * to another. The "old" flask cluster should be ignored by
   * the new flasks, and thus have their locations ignored
   * by chemist. If the old flasks are mirrored by the new
   * flasks (and vice versa), there would essentially be a
   * cascading amplication DOS attack of the system, by itself.
   */
  case object Ignored extends LocationIntent
}
