package oncue.svc.funnel.aws

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.autoscaling.{AmazonAutoScaling,AmazonAutoScalingClient}
import com.amazonaws.services.autoscaling.model.{AutoScalingGroup,Instance => ASGInstance}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

object ASG {

  case class Group(
    name: String,
    instances: Seq[Instance] = Nil,
    tags: Map[String,String] = Map.empty)

  case class Instance(
    id: String,
    internalHostname: Option[String] = None,
    externalHostname: Option[String] = None)

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

  def list(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Group]] = {
    def addresses(g: List[Group]): Task[Seq[Address]] = {
      val x: Seq[Task[Seq[Address]]] = g.flatMap(_.instances.map(i => EC2.instanceHostnames(i.id)(ec2)))
      Task.gatherUnordered(x).map(_.flatMap(identity))
    }

    def groups = Task {
      asg.describeAutoScalingGroups.getAutoScalingGroups.asScala.toList.map(g =>
        Group(name = g.getAutoScalingGroupName,
              instances = instances(g),
              tags = tags(g))
      )
    }

    for {
      g <- groups
      a <- addresses(g)
    } yield {
      println(">>>> " + a)

      g
    }
  }

  private def tags(g: AutoScalingGroup): Map[String,String] =
    g.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

  private def instances(g: AutoScalingGroup): Seq[Instance] =
    g.getInstances.asScala.map(i => Instance(i.getInstanceId))

  // private def hostnames(i: ASGInstance) = EC2.instance(i.getInstanceId)


}