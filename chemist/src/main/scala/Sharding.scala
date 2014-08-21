package oncue.svc.funnel.chemist


object Sharding {
  import scalaz.==>>
  // import java.net.URL
  import intelmedia.ws.funnel.BucketName

  type Flask = InstanceID
  type Distribution = InstanceID ==>> Set[SafeURL]

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def flasks(d: Distribution): Set[Flask] =
    sorted(d).keySet

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Map[Flask, Set[SafeURL]] =
    d.toList.sortBy(_._2.size).toMap

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[Flask, Set[SafeURL]] =
    d.toList.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def urls(d: Distribution): Set[SafeURL] =
    d.values.reduceLeft(_ ++ _)

  /**
   * Given the new set of urls to monitor, compute how said urls
   * should be distributed over the known flask instances
   */
  def calculate(s: Set[(BucketName, SafeURL)])(d: Distribution) = {
    val f = flasks(d)
    Stream.continually(s).flatten.zip(
      Stream.continually(f).flatten).take(s.size.max(f.size)).toList
  }

  def deduplicate(that: Set[(BucketName, SafeURL)])(d: Distribution) = //: Set[(BucketName, URL)] =
    (urls _ andThen (x => that.map(_._2) &~ x)
      ).apply(d)

}


