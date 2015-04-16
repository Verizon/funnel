package funnel
package chemist
package aws

import java.net.URL
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
class Discovery(ec2: AmazonEC2, asg: AmazonAutoScaling) extends chemist.Discovery {

  private val log = Logger[Discovery]

  ///////////////////////////// public api /////////////////////////////

  /**
   * List all of the instances in the given AWS account that respond to a rudimentry
   * verification that Funnel is running on port 5775 and is network accessible.
   */
  def list: Task[Seq[Instance]] =
    instances(_ => true)

  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupOne(id: InstanceID): Task[Instance] = {
    lookupMany(Seq(id)).flatMap {
      _.filter(_.id == id).headOption match {
        case None => Task.fail(InstanceNotFoundException(id))
        case Some(i) => Task.now(i)
      }
    }
  }

  /**
   * Lookup the `Instace` metadata for a set of `InstanceID`.
   * @see funnel.chemist.Deployed.lookupOne
   */
  def lookupMany(ids: Seq[InstanceID]): Task[Seq[Instance]] =
    for {
      a <- EC2.reservations(ids)(ec2)
      _  = log.debug(s"Deployed.lookupMany, a = ${a.length}")
      b <- Task.now(a.flatMap(_.getInstances.asScala.map(fromAWSInstance)))
      (fails,successes) = b.toVector.separate
      _  = log.debug(s"Deployed.lookupMany b = ${b.length}")
      _  = fails.foreach(x => log.error(x))
    } yield successes


  ///////////////////////////// filters /////////////////////////////

  def isFlask(i: Instance): Boolean =
    i.application.map(_.name.startsWith("flask")).getOrElse(false)


  ///////////////////////////// internal api /////////////////////////////

  private def instances(f: Instance => Boolean): Task[Seq[Instance]] =
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
  private def readAutoScallingGroups: Task[Seq[Instance]] =
    for {
      g <- ASG.list(asg)
      _  = log.debug(s"Found ${g.length} auto-scalling groups with ${g.map(_.instances.length).reduceLeft(_ + _)} instances...")
      r <- lookupMany(g.flatMap(_.instances.map(_.getInstanceId)))
    } yield r

  private def fromAWSInstance(in: AWSInstance): String \/ Instance = {
    val sgs: List[String] = in.getSecurityGroups.asScala.toList.map(_.getGroupName)
    val extdns: Option[String] =
      if(in.getPublicDnsName.nonEmpty) Option(in.getPublicDnsName) else None
    val intdns = Option(in.getPrivateDnsName)

    val host = (extdns orElse intdns) \/> s"instance had no ip: ${in.getInstanceId}"
    host map { h =>
      val loc = Location(
          host = h,
          port = 5775,
          datacenter = in.getPlacement.getAvailabilityZone,
          isPrivateNetwork = (extdns.isEmpty && intdns.nonEmpty)
      )
      val tloc = loc.copy(port=5776)
      Instance(
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
  private def validate(instance: Instance): Task[Instance] = {
    if(instance.location.isPrivateNetwork) Task.now(instance)
    else for {
      a <- Task(fetch(instance.asURI))(Chemist.defaultPool)
      b <- a.fold(e => Task.fail(e), o => Task.now(o))
    } yield instance
  }
}
