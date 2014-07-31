package oncue.svc.funnel
package object aws {
  type ARN = String

  import com.amazonaws.services.sns.AmazonSNSClient
  import com.amazonaws.services.sqs.AmazonSQSClient

  def createTopicWithSubscribedQueue(name: String
    )(sns: AmazonSNSClient, sqs: AmazonSQSClient) = {
    for {
      q <- SQS.create("testingq")(sqs)
      t <- SNS.create("testing")(sns)
      x <- SNS.subscribe(t,q,"sqs")(sns)
    } yield x
  }

}
