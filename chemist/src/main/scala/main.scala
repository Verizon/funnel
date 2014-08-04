package intelmedia.ws.funnel
package chemist

import oncue.svc.funnel.aws.SQS
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}

object Main {
  def main(args: Array[String]): Unit = {
    val Q = SQS.client(
      new BasicAWSCredentials(
        sys.env("AWS_ACCESS_KEY"),
        sys.env("AWS_SECRET_KEY")),
      region = Region.getRegion(Regions.fromName("us-east-1")))

    SQS.subscribe("https://sqs.us-east-1.amazonaws.com/465404450664/ops-chemist")(Q).run.run

    val url = "https://sqs.us-east-1.amazonaws.com/465404450664/ops-chemist"

    for {
      a <- SQS.subscribe(url)(Q)
      b <- SQS.deleteMessages(url, a)(Q)
    } yield ()


  }
}




