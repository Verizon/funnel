package funnel
package chemist
package aws

import java.net.{URI,URL}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.\/
import scalaz.std.vector._
import scalaz.stream.Process
import scalaz.syntax.monadPlus._
import scalaz.syntax.std.option._
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import journal.Logger
import funnel.aws._
import concurrent.duration._

/**
 * This module contains functions for describing the deployed world as the caller
 * sees it. Functions are provided to look up instance metadata for a single host,
 * multiple hosts, or listing the entire known world.
 *
 * The assumptions that have been made about hosts are:
 * 1. They are network accessible from the *Chemist* perspective
 * 2. They are running a Funnel server on port 5775 - this is used to validate that
 *    the instance is in fact a useful server for the sake of work sharding.
 */
class AwsDiscovery(
  ec2: AmazonEC2,
  asg: AmazonAutoScaling,
  cacheMaxSize: Int = 10000,
  cacheExpiryAfterTTL: Duration = 5.minutes) extends Discovery {

  private val log = Logger[Discovery]

  type AwsInstanceId = String

  val cache = Cache[AwsInstanceId, AwsInstance](
    maximumSize = Some(cacheMaxSize),
    expireAfterWrite = Some(cacheExpiryAfterTTL))

  ///////////////////////////// public api /////////////////////////////

  /**
   * List all of the instances in the given AWS account that respond to a rudimentry
   * verification that Funnel is running on port 5775 and is network accessible.
   */
  def listTargets: Task[Seq[(TargetID, Set[Target])]] =
    instances(!isFlask(_)).map(_.map(in => TargetID(in.id) -> in.targets))

  /**
   * List all of the instances in the given AWS account that respond to a rudimentry
   * verification that Funnel is running on port 5775 and is network accessible.
   */
  def listFlasks: Task[Seq[Flask]] =
    instances(isFlask).map(_.map(in => Flask(FlaskID(in.id), in.location, in.telemetryLocation)))

  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupFlask(id: FlaskID): Task[Flask] = {
    lookupMany(Seq(id.value)).flatMap {
      _.filter(_.id == id.value).headOption match {
        case None => Task.fail(InstanceNotFoundException(id.value))
        case Some(i) => Task.now(i.asFlask)
      }
    }
  }

  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupTargets(id: TargetID): Task[Set[Target]] = {
    lookupMany(Seq(id.value)).flatMap {
      _.filter(_.id == id.value).headOption match {
        case None    => Task.fail(InstanceNotFoundException(id.value))
        case Some(i) => Task.now(i.targets)
      }
    }
  }


  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  private def lookupOne(id: String): Task[AwsInstance] = {
    lookupMany(Seq(id)).flatMap {
      _.filter(_.id == id).headOption match {
        case None => Task.fail(InstanceNotFoundException(id))
        case Some(i) => Task.now(i)
      }
    }
  }

  /**
   * Lookup the `Instance` metadata for a set of `InstanceID`.
   * @see funnel.chemist.AwsDiscovery.lookupOne
   */
  private def lookupMany(ids: Seq[String]): Task[Seq[AwsInstance]] = {
    def lookInCache: (Seq[String],Seq[AwsInstance]) =
      ids.map(id => id -> cache.get(id)
        ).foldLeft[(Seq[String],Seq[AwsInstance])]((Seq.empty, Seq.empty)){ (a,b) =>
          val (ids,instances) = a
          b match {
            case (id,Some(instance)) => (ids, instances :+ instance)
            case (id,None) => (ids :+ id, instances)
          }
        }

    def lookInAws(specificIds: Seq[String]): Task[Seq[AwsInstance]] =
      for {
        a <- EC2.reservations(specificIds)(ec2)
        _  = log.debug(s"AwsDiscovery.lookupMany, a = ${a.length}")
        b <- Task.now(a.flatMap(_.getInstances.asScala.map(fromAWSInstance)))
        (fails,successes) = b.toVector.separate
        _  = log.debug(s"AwsDiscovery.lookupMany b = ${b.length}")
        _  = fails.foreach(x => log.error(x))
      } yield successes

    def updateCache(instances: Seq[AwsInstance]): Task[Seq[AwsInstance]] =
      Task.delay {
        log.debug("Updating the cache with ${instances.length} items.")
        instances.foreach(i => cache.put(i.id, i))
        instances
      }

    lookInCache match {
      // all found in cache
      case (Nil,found) =>
        log.debug(s"AwsDiscovery.lookupMany: all ${found.length} instances in the cache.")
        Task.now(found)

      // none found in cache
      case (missing,Nil) =>
        log.debug(s"AwsDiscovery.lookupMany: all ${missing.length} instances are missing in the cache.")
        lookInAws(missing).flatMap(updateCache)

      // partially found in cache
      case (missing,found) =>
        log.debug(s"AwsDiscovery.lookupMany: ${missing.length} missing. ${found.length} found in the cache.")
        lookInAws(missing)
          .flatMap(updateCache)
          .map(_ ++ found)
    }
  }

  ///////////////////////////// filters /////////////////////////////

  def isFlask(id: String): Task[Boolean] =
    lookupOne(id).map { i =>
      i.application.map(_.name.startsWith("flask")).getOrElse(false)
    }

  def isFlask(i: AwsInstance): Boolean =
    i.application.map(_.name.startsWith("flask")).getOrElse(false)

  ///////////////////////////// internal api /////////////////////////////

  private def instances(f: AwsInstance => Boolean): Task[Seq[AwsInstance]] =
    for {
      a <- readAutoScallingGroups
      // apply the specified filter if we want to remove specific groups for a reason
      x  = a.filter(f)
      // actually reach out to all the discovered hosts and check that their port is reachable
      y  = x.map(g => validate(g).attempt)
      // run the tasks on the specified thread pool (Server.defaultPool)
      b <- Task.gatherUnordered(y)
      r  = b.flatMap(_.toList)
      _  = log.debug(s"validated instance list: ${r.map(_.id).mkString(", ")}")
    } yield r

  /**
   * This is kind of horrible, but it is what it is. The AWS api's really do not help here at all.
   * Sorry!
   */
  private def readAutoScallingGroups: Task[Seq[AwsInstance]] =
    for {
      g <- ASG.list(asg)
      _  = log.debug(s"Found ${g.length} auto-scalling groups with ${g.map(_.instances.length).reduceLeft(_ + _)} instances...")
      r <- lookupMany(g.flatMap(_.instances.map(_.getInstanceId)))
    } yield r

  private def fromAWSInstance(in: AWSInstance): String \/ AwsInstance = {
    val sgs: List[String] = in.getSecurityGroups.asScala.toList.map(_.getGroupName)
    val intdns = Option(in.getPrivateDnsName)
    val host = intdns \/> s"instance had no ip: ${in.getInstanceId}"
    log.debug(s"fromAWSInstance, host = $host")
    host.map { h =>
      val loc = Location(
          host = h,
          port = 5775,
          datacenter = in.getPlacement.getAvailabilityZone,
          isPrivateNetwork = intdns.nonEmpty
      )
      // TODO, we'll need to do something different in the meesos world where ports are remappped
      val tloc = loc.copy(port=7390, protocol="tcp")
      AwsInstance(
        id = in.getInstanceId,
        location = loc,
        telemetryLocation = tloc,
        firewalls = sgs,
        tags = in.getTags.asScala.map(t => t.getKey -> t.getValue).toMap
      )
    }
  }

  import scala.io.Source
  import scalaz.\/

  private def fetch(url: URL): Throwable \/ Unit =
    \/.fromTryCatchThrowable[Unit, Exception] {
      val c = url.openConnection
      c.setConnectTimeout(300) // timeout in 300ms to keep the overhead reasonable
      c.setReadTimeout(300)
      c.connect()
    }

  /**
   * Goal of this function is to validate that the machine instances specified
   * by the supplied group `g`, are in fact running a funnel instance and it is
   * ready to start sending metrics if we connect to its `/stream` function.
   */
  private def validate(instance: AwsInstance): Task[AwsInstance] = {
    for {
      a <- Task(fetch(instance.asURI.toURL))(Chemist.defaultPool)
      b <- a.fold(e => Task.fail(e), o => Task.now(o))
    } yield instance
  }
}
