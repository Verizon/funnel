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
  resourceTemplates: Seq[LocationTemplate],
  cacheMaxSize: Int = 2000
) extends Discovery {

  type AwsInstanceId = String

  private val log = Logger[AwsDiscovery]

  private val cache = Cache[AwsInstanceId, AwsInstance](
    maximumSize = Some(cacheMaxSize))

  private val allTemplates: Map[NetworkScheme, Seq[LocationTemplate]] =
    NetworkScheme.all.foldLeft(Map.empty[NetworkScheme,Seq[LocationTemplate]]){ (a,b) =>
      a + (b -> resourceTemplates.filter(_.has(b)))
    }

  ///////////////////////////// public api /////////////////////////////

  /**
   * List all of the instances in the given AWS account that respond to a rudimentry
   * verification that Funnel is running on port 5775 and is network accessible.
   */
  def listTargets: Task[Seq[(TargetID, Set[Target])]] =
    instances(notFlask).map(_.map(in => TargetID(in.id) -> in.targets ))

  /**
   * List all of the instances in the given AWS account and figure out which ones of
   * those instances meets the classification criterion for being considered a flask.
   */
  def listActiveFlasks: Task[Seq[Flask]] =
    for {
      a <- instances(isActiveFlask)
      b  = a.flatMap(i => i.supervision.map(Flask(FlaskID(i.id), i.location, _)))
    } yield b

  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupFlask(id: FlaskID): Task[Flask] =
    for {
      a <- lookupOne(id.value)
      b <- a.supervision.map(Task.now).getOrElse(Task.fail(FlaskMissingSupervision(a)))
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
  protected def lookupOne(id: String): Task[AwsInstance] = {
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
  protected def lookupMany(ids: Seq[String]): Task[Seq[AwsInstance]] = {
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
        _  = log.warn(s"AwsDiscovery.lookupMany failed to validate ${fails.length} instances.")
        _  = log.debug(s"AwsDiscovery.lookupMany validated = ${b}")
        _  = fails.foreach(x => log.error(x))
      } yield successes

    def updateCache(instances: Seq[AwsInstance]): Task[Seq[AwsInstance]] =
      Task.delay {
        log.debug(s"Updating the cache with ${instances.length} items.")
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

  import InstanceClassifier._

  /**
   * Find all the flasks that are currently classified as active.
   * @see funnel.chemist.AwsDiscovery.classify
   */
  def isActiveFlask(c: InstanceClassifier): Boolean =
    c == ActiveFlask

  /**
   * The reason this is not simply the inverted version of `isActiveFlask`
   * is that when asking for targets, we specifically do not want any
   * Flasks, active or otherwise, because mirroring a Flask from another
   * Flask risks a cascading failure due to key amplication (essentially
   * mirroring the mirrorer whilst its mirroring etc).
   *
   * To mittigate this, we specifically call out anything that is not
   * classified as an `ActiveTarget`
   */
  def notFlask(c: InstanceClassifier): Boolean =
    c == ActiveFlask || c == InactiveFlask || c == Unknown

  /**
   * This default implementation does not properly handle the various upgrade
   * cases that you might encounter when migrating from one flask cluster to
   * another, but it instead provided as a default where all avalible flasks
   * are "active". There are a set of upgrade scenarios where you do not want
   * to mirror from an existing flask cluster, so they are not targets, nor are
   * they active flasks.
   *
   * Providing this with a task return type so that extensions can do I/O
   * if they need too (clearly a cache locally would be needed in that case)
   *
   * It is highly recomended you override this with your own classification logic.
   */
  val classify: Task[AwsInstance => InstanceClassifier] = {
    def isFlask(i: AwsInstance): Boolean =
      i.application.map(_.name.startsWith("flask")).getOrElse(false)

    Task.delay {
      instance =>
        if(isFlask(instance)) ActiveFlask
        else ActiveTarget
    }
  }

  ///////////////////////////// internal api /////////////////////////////

  private def instances(g: InstanceClassifier => Boolean): Task[Seq[AwsInstance]] =
    for {
      a <- readAutoScallingGroups
      b <- classify
      c  = b andThen g
      // apply the specified filter if we want to remove specific groups for a reason
      x  = a.filter(c(_))
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

  private[aws] def toLocation(
    dns: String,
    datacenter: String,
    template: Option[String],
    intent: LocationIntent): String \/ Location =
    for {
      a <- Option(dns).filter(_.nonEmpty
        ) \/> s"instance had no address: $dns"

      b <- template \/> s"supplied template was invalid. template = '$template'"

      c  = new URI(LocationTemplate(b).build("@host" -> a))

      d <- Location.fromURI(c, datacenter, intent, allTemplates
        ) \/> s"unable to create a location from uri '$c'"
    } yield d

  // should be overriden at deploy time, but this is just last resort fallback.
  private val defaultInstanceTemplate = Option("http://@host:5775")

  /**
   * The EC2 instance in question should have the following tags:
   *
   * 1. `funnel:mirror:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the mirroring endpoint. Example would
   * be `http://@host:5775`. Supported URI schemes are `http` and `tcp` (where the
   * latter is a zeromq PUB socket).
   *
   * 2. `funnel:telemetry:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the admin telemetry socket. Only valid
   * protocol is `tcp` for a zeromq PUB socket.
   */
  private def fromAWSInstance(in: AWSInstance): String \/ AwsInstance = {
    import LocationIntent._

    val machineTags = in.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

    val dns = in.getPrivateDnsName

    val datacenter = in.getPlacement.getAvailabilityZone

    val mirrorTemplate: Option[String] =
      machineTags.get(AwsTagKeys.mirrorTemplate) orElse defaultInstanceTemplate

    val supervisionTemplate: Option[String] =
      machineTags.get(AwsTagKeys.supervisionTemplate)

    for {
      a <- toLocation(dns, datacenter, mirrorTemplate, Mirroring)
      _  = log.debug(s"discovered mirrioring template '$a'")

      b  = toLocation(dns, datacenter, supervisionTemplate, Supervision)
      _  = b.foreach(t => log.debug(s"discovered telemetry template '$t'"))
    } yield AwsInstance(
      id = in.getInstanceId,
      tags = machineTags,
      locations = NonEmptyList(a, b.toList:_*)
    )
  }

  import scala.io.Source
  import scalaz.\/
  import java.net.{Socket, InetSocketAddress}

  /**
   * Goal of this function is to validate that the machine instances specified
   * by the supplied group `g`, are in fact running a funnel instance and it is
   * ready to start sending metrics if we connect to its `/stream` function.
   */
  private def validate(instance: AwsInstance): Task[AwsInstance] = {
    /**
     * Do a naieve check to see if the socket is even network accessible.
     * Given that funnel is using multiple protocols we can't assume that
     * any given protocol at this point in time, so we just try to see if
     * its even avalible on the port the discovery flow said it was.
     *
     * This mainly guards against miss-configuration of the network setup,
     * LAN-ACLs, firewalls etc.
     */
    def go(uri: URI): Throwable \/ Unit =
      \/.fromTryCatchThrowable[Unit, Exception]{
        val s = new Socket
        // timeout in 300ms to keep the overhead reasonable
        try s.connect(new InetSocketAddress(uri.getHost, uri.getPort), 300)
        finally s.close // whatever the outcome, close the socket to prevent leaks.
      }

    for {
      a <- Task(go(instance.location.uri))(Chemist.defaultPool)
      b <- a.fold(e => Task.fail(e), o => Task.now(o))
    } yield instance
  }
}
