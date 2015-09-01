package funnel
package aws

import scalaz.concurrent.Task
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.{AmazonCloudFormation,AmazonCloudFormationClient}
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest

object CFN {
  import scala.collection.JavaConverters._

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonCloudFormation = { //cfg.require[String]("aws.region"))
    val client = new AmazonCloudFormationClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  /**
   * Get a list of all the declared "outputs" for a given clouformation stack.
   * As far as chemist is concerened, this is needed because we want to figure
   * out where our SQS queue was created.
   */
  def getStackOutputs(name: String)(cfn: AmazonCloudFormation): Task[Map[String,String]] = {
    val req = (new DescribeStacksRequest).withStackName(name)
    for {
      a <- Task(cfn.describeStacks(req))(funnel.chemist.Chemist.defaultPool)
      b  = a.getStacks.asScala.flatMap(_.getOutputs.asScala)
    } yield b.map(x => x.getOutputKey -> x.getOutputValue).toMap
  }
}
