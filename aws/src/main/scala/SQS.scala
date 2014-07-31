package oncue.svc.funnel.aws

import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.AddPermissionRequest
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.BasicAWSCredentials
import scalaz.concurrent.Task

object SQS {
  private val accounts = List(
    "447570741169",
    "460423777025",
    "465404450664",
    "573879536903",
    "596986430194",
    "653211152919",
    "807520270390",
    "825665186404",
    "907213898261",
    "987980579136"
  )

  def client(
    credentials: BasicAWSCredentials,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonSQSClient = { //cfg.require[String]("aws.region"))
    val client = new AmazonSQSClient()
    client.setRegion(region)
    client
  }

  def create(queue: String)(client: AmazonSQSClient) =
    for {
      u <- Task(client.createQueue(queue).getQueueUrl)
      p  = new AddPermissionRequest(u, queue, accounts, List("SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility"))
      _ <- Task(client.addPermission(p))
    } yield ()

  def subscribe(queue: String)(client: AmazonSQSClient) = {

  }

}