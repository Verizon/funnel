package oncue.svc.funnel.chemist

import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import scalaz.stream.Process
import scalaz.concurrent.Task
import oncue.svc.funnel.aws.{SQS,SNS}

object Operations {



  // def setup(topicName: String, queueName: String)(sns: AmazonSNSClient, sqs: AmazonSQSClient): Task[Unit] = {
  //   for {
  //     a <- SNS.create(topicName)(sns)
  //     b <- SQS.create(queueName)(sqs)
  //     c <- SNS.subscribe(a, b)(sns)
  //     _ <- collateExistingWork
  //   } yield ()
  // }

  // TODO: Upon startup the system should ask all the flasks what they
  // are already monitoring so that the chemist has a full view on any
  // previously issued work.
  private def collateExistingWork: Task[Unit] = Task.now(())
}
