package oncue.svc.funnel.chemist

import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import oncue.svc.funnel.internals._

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
object Deployed {

  ///////////////////////////// public api /////////////////////////////

  /**
   * List all of the instances in the given AWS account that respond to a rudimentry
   * verification that Funnel is running on port 5775 and is network accessible.
   */
  def list(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Instance]] =
    instances(_ => true)(asg,ec2)


  /**
   * Lookup the `Instance` for a given `InstanceID`; `Instance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupOne(id: InstanceID)(ec2: AmazonEC2): Task[Instance] = {
    lookupMany(Seq(id))(ec2).flatMap {
      _.filter(_.id == id).headOption match {
        case None => Task.fail(new Exception("No instance found with that key."))
        case Some(i) => Task.now(i)
      }
    }
  }

  /**
   * Lookup the `Instace` metadata for a set of `InstanceID`.
   * @see oncue.svc.funnel.chemist.Deployed.lookupOne
   */
  def lookupMany(ids: Seq[InstanceID])(ec2: AmazonEC2): Task[Seq[Instance]] =
    for {
      a <- EC2.reservations(ids)(ec2)
      // _  = println(s"Deployed.lookupMany a=$a")
      b <- Task.now(a.flatMap(_.getInstances.asScala.map(fromAWSInstance)))
      // _  = println(s"Deployed.lookupMany b=$b")
    } yield b

  ///////////////////////////// filters /////////////////////////////

  object filter {
    def funnels(i: Instance): Boolean =
      i.firewalls.exists(_.toLowerCase == "monitor-funnel")

    def flasks(i: Instance): Boolean =
      i.application.map(_.name.startsWith("flask")).getOrElse(false)

    def chemists(i: Instance): Boolean =
      i.application.map(_.name.startsWith("chemist")).getOrElse(false)
  }


  ///////////////////////////// internal api /////////////////////////////

  private def instances(f: Instance => Boolean
    )(asg: AmazonAutoScaling, ec2: AmazonEC2
    ): Task[Seq[Instance]] =
    for {
      a <- readAutoScallingGroups(asg, ec2)
      // apply the specified filter if we want to remove specific groups for a reason
      x  = a.filter(f)
      // actually reach out to all the discovered hosts and check that their port is reachable
      y  = x.map(g => validate(g).attempt)
      // run the tasks on the specified thread pool (Server.defaultPool)
      b <- Task.gatherUnordered(y)
    } yield b.flatMap(_.toList)

  /**
   * This is kind of horrible, but it is what it is. The AWS api's really do not help here at all.
   * Sorry!
   */
  private def readAutoScallingGroups(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Instance]] =
    for {
      g <- ASG.list(asg)
      r <- lookupMany(g.flatMap(_.instances.map(_.getInstanceId)))(ec2)
    } yield r

  private def fromAWSInstance(in: AWSInstance): Instance = {
    val sgs: List[String] = in.getSecurityGroups.asScala.toList.map(_.getGroupName)
    val extdns: Option[String] =
      if(in.getPublicDnsName.nonEmpty) Option(in.getPublicDnsName) else None
    val intdns = Option(in.getPrivateDnsName)

    Instance(
      id = in.getInstanceId,
      location = Location(
        dns = extdns orElse intdns,
        datacenter = in.getPlacement.getAvailabilityZone,
        isPrivateNetwork = (extdns.isEmpty && intdns.nonEmpty)
      ),
      firewalls = sgs,
      tags = in.getTags.asScala.map(t => t.getKey -> t.getValue).toMap
    )
  }

  import scala.io.Source
  import scalaz.\/

  private def fetch(url: URL): Throwable \/ Unit =
    \/.fromTryCatch {
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
      a <- Task(instance.asURL.flatMap(fetch))(Server.defaultPool)
      b <- a.fold(e => Task.fail(e), o => Task.now(o))
    } yield instance
  }
}
