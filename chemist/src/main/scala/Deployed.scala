package oncue.svc.funnel.chemist

import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import oncue.svc.funnel.aws._

// for what follows, im sorry!
object Deployed {
  // private val defaultResources = Set(
  //   "stream/previous",
  //   "stream/sliding",
  //   "stream/uptime"
  // )

  // private def urlsForInstances(m: Map[String, Seq[Instance]], resources: Set[String]): Map[String,Seq[URL]] =
  //   m.map {
  //     case (k,v) => k -> v.flatMap(i => resources.flatMap(p => i.asURL(path = p).toList ))
  //   }

  def list(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Instance]] =
    instances(g => true)(asg,ec2)

  def funnels(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Instance]] =
    instances(filter.funnels)(asg,ec2)

  def flasks(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Instance]] =
    instances(filter.flasks)(asg,ec2)


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
      x <- EC2.reservations(g.flatMap(_.instances.map(_.getInstanceId)))(ec2)
    } yield {
      val instances: Map[InstanceID, AWSInstance] =
        x.flatMap(_.getInstances.asScala.toList)
          .groupBy(_.getInstanceId).mapValues(_.head)

      g.flatMap(extractInstance(_, instances))
    }

  private def extractInstance(group: Group, instances: Map[InstanceID, AWSInstance]): Seq[Instance] =
    group.instances.map { i =>
      val found = instances.get(i.getInstanceId)
      val sgs   = found.toList.flatMap(_.getSecurityGroups.asScala.toList).map(_.getGroupName)
      // serioulsy hate APIs that return emtpy string as their result.
      val extdns = found.flatMap(x =>
        if(x.getPublicDnsName.nonEmpty) Option(x.getPublicDnsName)
        else None)
      val intdns = found.map(_.getPrivateDnsName)

      Instance(
        id = i.getInstanceId,
        location = Location(
          dns = extdns orElse intdns,
          datacenter = i.getAvailabilityZone,
          isPrivateNetwork = (extdns.isEmpty && intdns.nonEmpty)
        ),
        firewalls = sgs,
        tags = group.tags
      )
    }

  object filter {
    def funnels(i: Instance): Boolean =
      i.firewalls.exists(_.toLowerCase == "monitor-funnel")

    def flasks(i: Instance): Boolean =
      i.application.map(_.name.startsWith("flask")).getOrElse(false)

    def chemists(i: Instance): Boolean =
      i.application.map(_.name.startsWith("chemist")).getOrElse(false)
  }

  // def periodic(delay: Duration)(asg: AmazonAutoScaling) =
  //   Process.awakeEvery(delay).evalMap(_ => list(asg))

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
