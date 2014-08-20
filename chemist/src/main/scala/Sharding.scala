package oncue.svc.funnel.chemist

object Sharding {
  import scalaz.==>>
  import java.net.URL
  import collection.immutable.SortedSet

  type Flask = InstanceID
  type Distribution = InstanceID ==>> Set[URL]

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def flasks(d: Distribution) =
    sorted(d).keySet

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Map[Flask, Set[URL]] =
    d.toList.sortBy(_._2.size).toMap

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[Flask, Set[URL]] =
    d.toList.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def urls(d: Distribution): Set[URL] =
    d.values.reduceLeft(_ ++ _)

}
