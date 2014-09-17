package oncue.svc.funnel
package object aws {

  type ARN = String

  import com.amazonaws.services.sns.AmazonSNSClient
  import com.amazonaws.services.sqs.AmazonSQSClient
  import scalaz.concurrent.Task

  def createTopicWithSubscribedQueue(name: String
    )(sns: AmazonSNSClient, sqs: AmazonSQSClient): Task[ARN] = {
    for {
      q <- SQS.create(name)(sqs)
      t <- SNS.create(name)(sns)
      x <- SNS.subscribe(t,q,"sqs")(sns)
    } yield x
  }
}
