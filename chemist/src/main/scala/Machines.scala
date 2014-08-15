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



object Machines {
  def listAll(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[URL]] =
    instances(g => true)(asg,ec2)

  def listFunnels(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[URL]] =
    instances(filter.funnels)(asg,ec2)

  def instances(f: Group => Boolean)(asg: AmazonAutoScaling, ec2: AmazonEC2) =
    readAutoScallingGroups(asg, ec2).map(
      _.filter(f).flatMap(_.instances).map(toUrl)
    )

  def readAutoScallingGroups(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Group]] =
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

  object filter {
    def funnels(g: Group): Boolean =
      g.securityGroups.exists(_.toLowerCase == "monitor-funnel")

    def flasks(g: Group): Boolean =
      g.application.map(_.startsWith("flask")).getOrElse(false)

    def chemists(g: Group): Boolean =
      g.application.map(_.startsWith("chemist")).getOrElse(false)
  }

  // def periodic(delay: Duration)(asg: AmazonAutoScaling) =
  //   Process.awakeEvery(delay).evalMap(_ => list(asg))

  private def toUrl(i: Instance): URL =
    new URL("http://${i.externalHostname}:5775/audit")

  import scala.io.Source

  private def fetch(url: URL): Task[String] = Task {
    val c = url.openConnection
    c.setConnectTimeout(500) // timeout in 500ms to keep the overhead reasonable
    Source.fromInputStream(c.getInputStream).mkString
  }

}
