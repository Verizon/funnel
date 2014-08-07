// package intelmedia.ws.funnel
// package chemist

// import java.io.File
// import java.net.URL
// import com.amazonaws.auth.BasicAWSCredentials
// import com.amazonaws.regions.{Regions,Region}
// import com.amazonaws.services.sns.AmazonSNSClient
// import com.amazonaws.services.sqs.AmazonSQSClient
// import com.amazonaws.services.sqs.model.Message
// import oncue.svc.funnel.aws.{SQS,SNS}
// import scalaz.concurrent.Task
// import scalaz.stream.{Process,Sink}
// import scalaz.{\/, -\/, \/-}

// object Chemist {

//   def flaskLifecycleStream(queueName: String)(sqs: AmazonSQSClient): Process[Task,Unit] =
//     for {
//       a <- SQS.subscribe(queueName)(sqs)
//       // _ <- Process.eval(myFunc(a.map(_.getMessageId)))
//       b <- SQS.deleteMessages(queueName, a)(sqs)
//     } yield ()

//   def setup(topicName: String, queueName: String)(sns: AmazonSNSClient, sqs: AmazonSQSClient): Task[Unit] = {
//     for {
//       a <- SNS.create(topicName)(sns)
//       b <- SQS.create(queueName)(sqs)
//       c <- SNS.subscribe(a, b)(sns)
//       _ <- collateExistingWork
//     } yield ()
//   }

//   // TODO: Upon startup the system should ask all the flasks what they
//   // are already monitoring so that the chemist has a full view on any
//   // previously issued work.
//   private def collateExistingWork: Task[Unit] = Task.now(())
// }

// object Main {

//   def main(args: Array[String]): Unit = {
//     import knobs._
//     import Chemist._

//     val cfg =
//       (knobs.loadImmutable(List(Required(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))))) or
//       knobs.loadImmutable(List(Required(ClassPathResource("oncue/chemist.cfg"))))).run

//     val sns = SNS.client(
//       new BasicAWSCredentials(
//         cfg.require[String]("aws.access-key"),
//         cfg.require[String]("aws.secret-key")),
//       cfg.lookup[String]("aws.proxy-host"),
//       cfg.lookup[Int]("aws.proxy-port"),
//       cfg.lookup[String]("aws.proxy-protocol"),
//       Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
//     )

//     val sqs = SQS.client(
//       new BasicAWSCredentials(
//         cfg.require[String]("aws.access-key"),
//         cfg.require[String]("aws.secret-key")),
//       cfg.lookup[String]("aws.proxy-host"),
//       cfg.lookup[Int]("aws.proxy-port"),
//       cfg.lookup[String]("aws.proxy-protocol"),
//       Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
//     )

//     // val url = "https://sqs.us-east-1.amazonaws.com/465404450664/ops-chemist"

//     flaskLifecycleStream(cfg.require[String]("chemist.sqs-queue-name"))(sqs)

//     setup(
//       cfg.require[String]("chemist.sns-topic-name"),
//       cfg.require[String]("chemist.sqs-queue-name"))(sns, sqs)

//     // exe.run.run
//   }

//   // def myFunc(m: List[String]): Task[Unit] =
//   //   Task {
//   //     println(s"<<< $m >>>>")
//   //   }

//   def init(): Unit = {
//     //
//   }
// }




