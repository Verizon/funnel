package oncue.svc.funnel.chemist

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import oncue.svc.funnel.aws._

object OperationalMachines {
  def listAll(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Group]] =
    for {
      g <- ASG.list(asg)
      x <- EC2.reservations(g.flatMap(_.instances.map(_.id)))(ec2)
    } yield {
      val instances: Map[String, AWSInstance] = x.flatMap(_.getInstances.asScala.toList)
                      .groupBy(_.getInstanceId).mapValues(_.head)

      g.map { grp =>
        grp.copy(
          instances = grp.instances.map { i =>
            val found = instances.get(i.id)
            val sgs = found.toList.flatMap(_.getSecurityGroups.asScala.toList).map(_.getGroupName)
            i.copy(
              internalHostname = found.map(_.getPrivateDnsName),
              externalHostname = found.map(_.getPublicDnsName),
              securityGroups   = sgs
            )
          }.toList
        )
      }
    }

  object filters {
    def funnels: Group => Boolean =
      _.securityGroups.exists(_.toLowerCase == "monitor-funnel")

    def flasks: Group => Boolean =
      _.application.map(_.startsWith("flask")).getOrElse(false)

    def chemists: Group => Boolean =
      _.application.map(_.startsWith("chemist")).getOrElse(false)
  }

  // def periodic(delay: Duration)(asg: AmazonAutoScaling) =
  //   Process.awakeEvery(delay).evalMap(_ => list(asg))

}
