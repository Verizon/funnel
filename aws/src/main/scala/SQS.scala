package funnel

import com.amazonaws.services.sqs.{AmazonSQS,AmazonSQSClient}
import com.amazonaws.services.sqs.model.{
  AddPermissionRequest,
  CreateQueueRequest,
  GetQueueAttributesRequest,
  Message,
  ReceiveMessageRequest,
  DeleteMessageBatchRequestEntry,
  DeleteMessageBatchResult}
import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.BasicAWSCredentials
import scalaz.concurrent.{Strategy,Task}
import scala.collection.JavaConverters._
import concurrent.duration._
import java.util.concurrent.{ExecutorService,ScheduledExecutorService}
import aws.ARN

object SQS {
  // hard-coded for now as these are so slow moving.
  private val accounts =
    List(
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

  private val permissions = List(
    "SendMessage",
    "ReceiveMessage",
    "DeleteMessage",
    "ChangeMessageVisibility",
    "GetQueueAttributes",
    "GetQueueUrl")

  private val readInterval = 12.seconds

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonSQS = { //cfg.require[String]("aws.region"))
    val client = new AmazonSQSClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  def arnForQueue(url: String)(client: AmazonSQS): Task[ARN] = {
    Task {
      val attrs = client.getQueueAttributes(
        new GetQueueAttributesRequest(url, List("QueueArn").asJava)).getAttributes.asScala
      attrs.get("QueueArn")
    }.flatMap {
      case None => Task.fail(new RuntimeException("The specified URL did not have an associated SQS ARN in the specified region."))
      case Some(m) => Task.now(m)
    }
  }

  import com.amazonaws.auth.policy.{Principal,Policy,Statement}, Statement.Effect
  import com.amazonaws.auth.policy.conditions.ConditionFactory
  import com.amazonaws.auth.policy.actions.SQSActions
  import com.amazonaws.auth.policy.Resource

  /**
   * This is kind of tricky. Basically we want the queue to be "public" for sending,
   * but only allow public senders that have an origin of a specific ARN. As usual,
   * this is ass-about-face AWS API terminology. In addition to adding this special
   * case for ASG event notifications, we also add all our known account IDs for
   * general administration purposes (chemist itself needs to hide messages etc).
   */
  private def policy(snsArn: ARN, sqsArn: ARN): Policy =
    new Policy().withStatements(
      new Statement(Effect.Allow)
        .withPrincipals(Principal.AllUsers)
        .withActions(SQSActions.SendMessage)
        .withResources(new Resource(sqsArn))
        .withConditions(ConditionFactory.newSourceArnCondition(snsArn))//,
      // new Statement(Effect.Allow)
      //   .withPrincipals(accounts.sorted.map(new Principal(_)):_*)
      //   .withActions(
      //     SQSActions.SendMessage,
      //     SQSActions.ReceiveMessage,
      //     SQSActions.DeleteMessage,
      //     SQSActions.ChangeMessageVisibility,
      //     SQSActions.GetQueueAttributes,
      //     SQSActions.GetQueueUrl)
    )

  // http://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-long-polling.html
  def create(queueName: String, snsArn: ARN)(client: AmazonSQS): Task[ARN] = {
    for {
      u <- Task(client.createQueue(queueName).getQueueUrl)
      a <- arnForQueue(u)(client)

      attrs = Map(
        "DelaySeconds"                  -> "120", // wait two minutes before making this message visibile to consumers so service has time to boot
        "MaximumMessageSize"            -> "64000",
        "MessageRetentionPeriod"        -> "1800",
        "ReceiveMessageWaitTimeSeconds" -> (readInterval.toSeconds - 2).toString,
        "Policy"                        -> policy(snsArn, a).toJson
      )

      // doing this independantly from the create queue request because we need a very
      // specific set of AWS policies to make the system work as needed.
      _ <- Task(client.setQueueAttributes(u, attrs.asJava))

    } yield a
  }

  import scalaz.stream.Process

  def subscribe(
    url: String,
    tick: Duration = readInterval,
    visibilityTimeout: Duration = 20.seconds
  )(client: AmazonSQS)(
    pool: ExecutorService,
    schedulingPool: ScheduledExecutorService
  ): Process[Task, List[Message]] = {
    Process.awakeEvery(tick)(Strategy.Executor(pool), schedulingPool).evalMap { _ =>
      Task {
        val req = (new ReceiveMessageRequest
          ).withQueueUrl(url
          ).withVisibilityTimeout(visibilityTimeout.toSeconds.toInt)

        val msgs: List[Message] =
          client.receiveMessage(req).getMessages.asScala.toList

        // println("sqs messages recieved count: " + msgs.length)
        // println("sqs messages: " + msgs)

        msgs
      }(pool)
    }
  }

  case class FailedDeletions(messageIds: List[String]) extends RuntimeException

  def deleteMessages(queue: String, msgs: List[Message])(sqs: AmazonSQS): Process[Task, Unit] = {
    val result: Task[Unit] = Task {
      val req = msgs.map(m => new DeleteMessageBatchRequestEntry(m.getMessageId, m.getReceiptHandle))

      if(msgs.nonEmpty){
        val res = sqs.deleteMessageBatch(queue, req.asJava)
        res.getFailed.asScala.toList match {
          case Nil    => Task.now(())
          case errors => Task.fail(FailedDeletions(errors.map(_.getId)))
        }
        ()
      } else ()
    }

    Process.eval(result)
  }

}
