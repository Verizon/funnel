package intelmedia.ws.funnel
package chemist

import oncue.svc.funnel.aws.SQS
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import scalaz.concurrent.Task
import scalaz.stream.Process

object Main {
  def main(args: Array[String]): Unit = {
    val Q = SQS.client(
      new BasicAWSCredentials(
        sys.env("AWS_ACCESS_KEY"),
        sys.env("AWS_SECRET_KEY")),
      region = Region.getRegion(Regions.fromName("us-east-1")))

    val url = "https://sqs.us-east-1.amazonaws.com/465404450664/ops-chemist"

    val exe = for {
      a <- SQS.subscribe(url)(Q)
      _ = println(s"================ $a")
      _ <- Process.eval(myFunc(a.map(_.getMessageId)))
      b <- SQS.deleteMessages(url, a)(Q)
    } yield ()


    exe.run.run
  }

  def myFunc(m: List[String]): Task[Unit] =
    Task {
      println(s"<<< $m >>>>")
    }

  def init(): Unit = {
    //
  }
}




