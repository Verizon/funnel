package funnel
package aws

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.autoscaling.{AmazonAutoScaling,AmazonAutoScalingClient}
import com.amazonaws.services.autoscaling.model.{
  DescribeAutoScalingGroupsRequest,
  AutoScalingGroup,Instance => ASGInstance}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

case class Group(
  name: String,
  instances: Seq[ASGInstance] = Nil,
  tags: Map[String,String] = Map.empty)

case class ASGNotFoundException(asgName: String) extends RuntimeException {
  override def getMessage: String = s"Unable to find the specified ASG in AWS: '$asgName'"
}

object ASG {

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonAutoScaling = { //cfg.require[String]("aws.region"))
    val client = new AmazonAutoScalingClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  import annotation.tailrec

  def list(asg: AmazonAutoScaling): Task[Seq[Group]] = {
    @tailrec def fetch(result: => Seq[AutoScalingGroup], token: Option[String] = None): Seq[AutoScalingGroup] = {
      val req = new DescribeAutoScalingGroupsRequest().withMaxRecords(100)
      val r = token.map(t => asg.describeAutoScalingGroups(req.withNextToken(t))
          ).getOrElse(asg.describeAutoScalingGroups(req))
      val l = r.getAutoScalingGroups.asScala.toList

      val aggregated = l ++ result

      if(r.getNextToken != null) fetch(aggregated, Option(r.getNextToken))
      else aggregated
    }

    Task(fetch(Nil).map(g =>
      Group(
        name      = g.getAutoScalingGroupName,
        instances = g.getInstances.asScala.toSeq,
        tags      = tags(g))
      ))
  }

  def lookupByName(name: String)(asg: AmazonAutoScaling): Task[AutoScalingGroup] = {
    val req = (new DescribeAutoScalingGroupsRequest
      ).withMaxRecords(1
      ).withAutoScalingGroupNames(name)

    Task(asg.describeAutoScalingGroups(req)).flatMap { r =>
      val opt: Option[AutoScalingGroup] = r.getAutoScalingGroups.asScala.toList.headOption
      val fail: Task[AutoScalingGroup] = Task.fail(ASGNotFoundException(name))
      opt.fold(fail)(Task.delay(_))
    }
  }

  private def tags(g: AutoScalingGroup): Map[String,String] =
    g.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

}
