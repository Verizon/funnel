package oncue.svc.funnel.aws

import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.services.sns.{AmazonSNS,AmazonSNSClient}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sns.model.{CreateTopicRequest, PublishRequest}
import com.amazonaws.auth.BasicAWSCredentials
import scalaz.concurrent.Task

object SNS {

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonSNS = { //cfg.require[String]("aws.region"))
    val client = new AmazonSNSClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  def create(topicName: String)(client: AmazonSNS): Task[ARN] = Task {
    val req = new CreateTopicRequest(topicName) // cfg.require[String]("aws.snsTopic")
    client.createTopic(req).getTopicArn // idempotent operation
  }

  def publish(topicName: String, payload: String)(client: AmazonSNS): Task[Unit] = {
    create(topicName)(client).flatMap { arn =>
      Task {
        val preq = new PublishRequest(arn, payload)
        val pres = client.publish(preq)
      }
    }
  }

  def subscribe(snsArn: ARN, targetArn: ARN, protocol: String = "sqs")(client: AmazonSNS): Task[ARN] =
    for {
      a <- Task(client.subscribe(snsArn, protocol, targetArn).getSubscriptionArn)
      b <- Task(client.setSubscriptionAttributes(a, "RawMessageDelivery", "true"))
    } yield a
}
