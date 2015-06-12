package funnel
package chemist
package aws

import java.net.{URI,URL}
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.{\/,NonEmptyList}
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
    for {
      a <- instances(isFlask)
      b  = a.flatMap(i => i.supervision.map(Flask(FlaskID(i.id), i.location, _)))
    } yield b

  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupFlask(id: FlaskID): Task[Flask] =
    for {
      a <- lookupOne(id.value)
      b <- a.supervision.map(Task.now).getOrElse(Task.fail(InstanceNotFoundException(id.value)))
    } yield Flask(FlaskID(a.id), a.location, b)

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
        instances.foreach(i => cache.put(i.id, i))
        instances
      }

    lookInCache match {
      // all found in cache
      case (Nil,found) =>
        Task.now(found)

      // none found in cache
      case (missing,Nil) =>
        lookInAws(missing).flatMap(updateCache)

      // partially found in cache
      case (missing,found) =>
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

  // should be overriden at deploy time, but this is just last resort fallback.
  private val defaultInstanceTemplate = Option("http://{host}:5775")

  /**
   * The EC2 instance in question should have the following tags:
   *
   * 1. `funnel:mirror:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the mirroring endpoint. Example would
   * be `http://{host}:5775`. Supported URI schemes are `http` and `tcp` (where the
   * latter is a zeromq PUB socket).
   *
   * 2. `funnel:telemetry:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the admin telemetry socket. Only valid
   * protocol is `tcp` for a zeromq PUB socket.
   */
  private def fromAWSInstance(in: AWSInstance): String \/ AwsInstance = {
    import LocationIntent._
    def toLocation(template: String, intent: LocationIntent)(tags: Map[String,String]): String \/ Location =
      for {
        a <- Option(in.getPrivateDnsName) \/> s"instance had no ip: ${in.getInstanceId}"
        b <- tags.get(template).orElse(defaultInstanceTemplate) \/> s"unable to find template for '$template'"
        c  = new URI(b.replace("@host", b))
        d <- Location.fromURI(c, in.getPlacement.getAvailabilityZone, intent) \/> s"unable to create a Location from URI '$c'"
      } yield d

    val machineTags = in.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

    for {
      a <- toLocation("funnel:mirror:uri-template", Mirroring)(machineTags)
      _  = log.debug(s"discovered mirrioring template '$a'")
      b  = toLocation("funnel:telemetry:uri-template", Supervision)(machineTags).toOption
      _  = log.debug(s"discovered telemetry template '$b'")
    } yield AwsInstance(
        id = in.getInstanceId,
        tags = machineTags,
        locations = NonEmptyList(a, b.toList:_*))
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
