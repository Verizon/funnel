package oncue.svc.funnel.chemist

import scalaz.==>>
import scalaz.std.string._
import scalaz.concurrent.Task
import oncue.svc.funnel.BucketName
import journal.Logger

object Sharding {

  type Flask = InstanceID
  type Distribution = Flask ==>> Set[Target]

  object Distribution {
    def empty: Distribution = ==>>()
  }

  case class Target(bucket: BucketName, url: SafeURL)
  object Target {
    val defaultResources = Seq("stream/previous")

    def fromInstance(resources: Seq[String] = defaultResources)(i: Instance): Set[Target] =
      (for {
        a <- i.application
        b <- i.asURL.toOption
      } yield resources.map(r => Target(a.toString, SafeURL(b+r))).toSet
      ).getOrElse(Set.empty[Target])
  }

  private lazy val log = Logger[Sharding.type]

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def shards(d: Distribution): Set[Flask] =
    sorted(d).keySet

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Map[Flask, Set[Target]] =
    d.toList.sortBy(_._2.size).toMap

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[Flask, Map[BucketName, List[SafeURL]]] =
    d.toList.map { case (i,s) =>
      i -> s.groupBy(_.bucket).mapValues(_.toList.map(_.url))
    }.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def targets(d: Distribution): Set[Target] =
    d.values match {
      case Nil   => Set.empty[Target]
      case other => other.reduceLeft(_ ++ _)
    }

  /**
   * provide the new distribution based on the result of calculating
   * how the new set should actually be distributed. main benifit here
   * is simply making the operations opaque (handling missing key cases)
   *
   * Returns values of this function represent two things:
   * 1. `Seq[(Flask,Target)]` is a sequence of targets zipped with the flask it was assigned too.
   * 2. `Distribution` is that same sequence folded into a `Distribution` instance which can
   *     then be added to the existing state of the world.
   */
  def distribution(s: Set[Target])(d: Distribution): (Seq[(Flask,Target)], Distribution) = {
    // this check is needed as otherwise the fold gets stuck in a gnarly
    // infinate loop, and this function never completes.
    if(s.isEmpty) (Seq.empty,d)
    else {
      log.debug(s"Sharding.distribution: attempting to distribute targets '${s.mkString(",")}'")
      val work = calculate(s)(d)

      log.debug(s"Sharding.distribution: work = $work")

      val dist = work.foldLeft(Distribution.empty){ (a,b) =>
        a.alter(b._1, _ match {
          case Some(s) => Option(s + b._2)
          case None    => Option(Set(b._2))
        })
      }

      log.debug(s"work = $work, dist = $dist")

      (work, dist)
    }
  }

  /**
   * Given the new set of urls to monitor, compute how said urls
   * should be distributed over the known flask instances
   */
  private[chemist] def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val servers = shards(d)
    val ss      = servers.size
    val input   = deduplicate(s)(d)
    val is      = input.size // caching operation as its O(n)
    val foo = if(is < ss) servers.take(is) else servers

    log.debug(s"calculating the target distribution: servers=$servers, input=$input")

    if(ss == 0){
      log.warn("there are no flask servers currently registered to distribute work too.")
      Nil // needed for when there are no Flask's in-memory; causes SOE.
    } else {
      // interleave the input with the known flask servers ordered by the
      // flask that currently has the least amount of work assigned.
      Stream.continually(input).flatten.zip(
        Stream.continually(foo).flatten).take(is.max(foo.size)
          ).toList.map(t => (t._2, t._1))
    }
  }

  /**
   * Given a set of inputs, check against the current known set of urls
   * that we're not already monitoring the inputs (thus ensuring that
   * the cluster is not having duplicated monitoring items)
   */
  private[chemist] def deduplicate(next: Set[Target])(d: Distribution): Set[Target] = {
    // get the full list of targets we currently know about
    val existing = targets(d)

    // determine if any of the supplied urls are existing targets
    val delta = next.map(_.url) &~ existing.map(_.url)

    // having computed the targets that we actually care about,
    // rehydrae a `Set[Target]` from the given `Set[SafeURL]`
    delta.foldLeft(Set.empty[Target]){ (a,b) =>
      a ++ next.filter(_.url == b)
    }
  }

  ///////////////////////// IO functions ///////////////////////////

  /**
   * The goal here is given the `Set[Target]` be able to compute how things
   * should be sharded, update the state, and then yield the results such that
   * its possible to test to the nearest edge of the world without actually
   * doing the side effect and calling the shards to assign the work (which would
   * of course result in `Task[Unit]` and be a nightmare to test)
   */
  def locateAndAssignDistribution(t: Set[Target], r: Repository): Task[Map[Location, Seq[Target]]] = {
    for {
      // read the current distribution from the repository
      x    <- r.distribution
      // compute a new distribution given these new inputs
      (a,d) = distribution(t)(x)
      _     = log.debug(s"Sharding.distribute a=$a, d=$d")
      // given we only want to issue commands to flasks once, lets bunch
      // up those targets that have been assigned to the same flask so we
      // can send them all over as a batch
      grouped: Map[Flask, Seq[Target]] = a.groupBy(_._1).mapValues(_.map(_._2))
      _ = log.debug(s"Sharding.distribute,grouped = $grouped")

      tasks = grouped.map { case (f,seq) =>
        r.instance(f).map(_.location).map((_,seq))
      }
      // pre-emtivly update our new state as the sending will take longer
      // than updating and we dont want the world to change under our feet
      _ <- r.mergeDistribution(d)
      x <- Task.gatherUnordered(tasks.toList)
    } yield x.toMap
  }

  /**
   * Given a computer set of locations -> targets, actually send them to the
   * relevant shard node API.
   */
  def distribute(locations: Map[Location, Seq[Target]]): Task[List[String]] =
    Task.gatherUnordered(locations.map { case (loc,targets) =>
      send(loc,targets) }.toSeq)

  /**
   * Given a collection of flask instances, find out what exactly they are already
   * mirroring and absorb that into the view of the world.
   *
   * This function should only really be used startup of chemist.
   */
  def gatherAssignedTargets(instances: Seq[Instance]): Task[Distribution] =
    for {
      a <- Task.gatherUnordered(instances.map(
            i => requestAssignedTargets(i.location).map(i.id -> _)))
    } yield a.foldLeft(Distribution.empty){ (a,b) =>
      a.alter(b._1, o => o.map(_ ++ b._2) orElse Some(Set.empty[Target]) )
    }

  import oncue.svc.funnel.http.{Bucket,JSON => HJSON}

  /**
   * Call out to the specific location and grab the list of things the flask
   * is already mirroring.
   */
  private def requestAssignedTargets(location: Location): Task[Set[Target]] = {
    import argonaut._, Argonaut._, JSON._, HJSON._
    import dispatch._, Defaults._

    for {
      a <- location.asURL(path = "mirror/sources").fold(Task.fail(_), Task.now(_))
      _  = log.debug(s"attempting to fetch sources from $a")

      b  = url(a.toString)
      _  = log.debug(s"requesting assigned targets from $a")

      c <- fromScalaFuture(Http(b OK as.String))
    } yield {
      Parse.decodeOption[List[Bucket]](c
        ).toList.flatMap(identity
        ).foldLeft(Set.empty[Target]){ (a,b) =>
          b.urls.map(s => Target(b.label, SafeURL(s))).toSet
        }
    }
  }

  /**
   * Touch the network and do the I/O using Dispatch.
   */
  private def send(to: Location, targets: Seq[Target]): Task[String] = {
    import dispatch._, Defaults._
    import argonaut._, Argonaut._
    import JSON.BucketsToJSON

    // FIXME: "safe" because we know we're passing in the default localhost
    // val host: HostAndPort = to.dns.map(_ + ":" + to.port).get
    val payload: Map[BucketName, List[SafeURL]] =
      targets.groupBy(_.bucket).mapValues(_.map(_.url).toList)

    for {
      a <- to.asURL(path = "mirror").fold(Task.fail(_), Task.now(_))
      b  = url(a.toString) << payload.toList.asJson.nospaces
      _  = log.debug(s"submitting to $a: $payload")
      c <- fromScalaFuture(Http(b OK as.String))
    } yield c
  }

}


