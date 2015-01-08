package oncue.svc.laboratory

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.{AmazonEC2,AmazonEC2Client}
import com.amazonaws.services.ec2.model.{
  DescribeAddressesRequest,
  DescribeAddressesResult,
  Filter,
  Address,
  DescribeInstancesRequest,
  DescribeInstancesResult,
  Reservation}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

object EC2 {

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

  import annotation.tailrec

  def reservations(ids: Seq[String])(ec2: AmazonEC2): Task[Seq[Reservation]] = {
    @tailrec def fetch(result: Seq[Reservation], token: Option[String] = None): Seq[Reservation] = {
      val req = new DescribeInstancesRequest().withInstanceIds(ids:_*)
      val r = token.map(t => ec2.describeInstances(req.withNextToken(t))
          ).getOrElse(ec2.describeInstances(req))
      val l = r.getReservations.asScala.toSeq

      val aggregated = l ++ result

      if(r.getNextToken != null) fetch(aggregated, Option(r.getNextToken))
      else aggregated
    }

    Task(fetch(Nil))
  }
}