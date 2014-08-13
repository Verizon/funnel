package oncue.svc.funnel.aws

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.{AmazonEC2,AmazonEC2Client}
import com.amazonaws.services.ec2.model.{
  DescribeAddressesRequest,
  DescribeAddressesResult,
  Filter,
  Address}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

object EC2 {

  case class Hostnames(external: Option[String], internal: Option[String])

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonEC2 = { //cfg.require[String]("aws.region"))
    val client = new AmazonEC2Client(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  def instanceHostnames(id: String)(ec2: AmazonEC2): Task[Seq[Address]] = Task {
    ec2.describeAddresses(new DescribeAddressesRequest()).getAddresses.asScala
  }

  private def filter(n: String) = new Filter().withName(n)

  // def list(client: AmazonAutoScaling): Task[Seq[AutoScalingGroup]] =
  //   Task(client.describeAutoScalingGroups.getAutoScalingGroups)

}