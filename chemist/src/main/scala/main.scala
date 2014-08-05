package intelmedia.ws.funnel
package chemist

import oncue.svc.funnel.aws.{SQS,SNS}
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import java.io.File

object Chemist {
  def incomingEventStream(url: String)(sqs: AmazonSQSClient): Process[Task,Unit] =
    for {
      a <- SQS.subscribe(url)(sqs)
      _ <- Process.eval(myFunc(a.map(_.getMessageId)))
      b <- SQS.deleteMessages(url, a)(sqs)
    } yield ()

  def setup(streamName: String)(sns: AmazonSNSClient, sqs: AmazonSQSClient): Task[Unit] = {
    for {
      a <- SNS.create(streamName)(sns)
      b <- SQS.create(streamName)(sqs)
      c <- SNS.subscribe(a, b)(sns)
    } yield ()
  }
}

object Main {

  def main(args: Array[String]): Unit = {
    import knobs._

    val cfg =
      (knobs.loadImmutable(List(Required(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))))) or
      knobs.loadImmutable(List(Required(ClassPathResource("oncue/chemist.cfg"))))).run

    val sns = SNS.client(
      new BasicAWSCredentials(
        cfg.require[String]("aws.access-key"),
        cfg.require[String]("aws.secret-key")),
      cfg.lookup[String]("aws.proxy-host"),
      cfg.lookup[Int]("aws.proxy-port"),
      cfg.lookup[String]("aws.proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
    )

    val sqs = SQS.client(
      new BasicAWSCredentials(
        cfg.require[String]("aws.access-key"),
        cfg.require[String]("aws.secret-key")),
      cfg.lookup[String]("aws.proxy-host"),
      cfg.lookup[Int]("aws.proxy-port"),
      cfg.lookup[String]("aws.proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
    )

    // val url = "https://sqs.us-east-1.amazonaws.com/465404450664/ops-chemist"
    val url = cfg.require[String]("chemist.stream-name")

    Chemist.incomingEventStream(url)(sqs)

    // exe.run.run
  }

  def myFunc(m: List[String]): Task[Unit] =
    Task {
      println(s"<<< $m >>>>")
    }

  def init(): Unit = {
    //
  }
}




