package oncue.svc.funnel.aws

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.{AmazonEC2,AmazonEC2Client}
// import com.amazonaws.services.ec2.model.AutoScalingGroup
import scalaz.concurrent.Task

object EC2 {

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

  def instance()

  // def list(client: AmazonAutoScaling): Task[Seq[AutoScalingGroup]] =
  //   Task(client.describeAutoScalingGroups.getAutoScalingGroups)

}