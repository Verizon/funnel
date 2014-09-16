package oncue.svc.funnel.chemist

import java.net.URL
import com.amazonaws.services.sqs.model.Message
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import scalaz.{~>,Free,Functor,\/,-\/,\/-}, Free.Return, Free.Suspend
import Sharding.{Target,Distribution}

object Server {
  type Server[A] = Free[ServerF, A]

  // ServerF algebra
  sealed trait ServerF[+A]{
    def map[B](f: A => B): ServerF[B]
  }

  case class Distribute[A](urls: Set[Target], k: A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = Distribute(urls, g(k))
  }
  case class DescribeDistribution[A](k: Map[InstanceID, Map[String, List[SafeURL]]] => A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = DescribeDistribution(k andThen g)
  }
  case class DescribeShards[A](k: Seq[Instance] => A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = DescribeShards(k andThen g)
  }

  ////////////// free monad plumbing //////////////

  private def liftF[A](srv: ServerF[A]): Free[ServerF,A] =
    Suspend[ServerF, A](Functor[ServerF].map(srv)(a => Return[ServerF, A](a)))

  private implicit def serverFFunctor[B]: Functor[ServerF] = new Functor[ServerF]{
    def map[A,B](fa: ServerF[A])(f: A => B): ServerF[B] = fa.map(f)
  }

  ////////////// public api / syntax ///////////////

  def distribution: Server[Map[InstanceID, Map[String, List[SafeURL]]]] =
    liftF(DescribeDistribution(identity))

  def distribute(urls: Set[Target]): Server[Unit] =
    liftF(Distribute(urls, ()))

  def shards: Server[Seq[Instance]] =
    liftF(DescribeShards(identity))

  def shard(id: InstanceID): Server[Option[Instance]] =
    liftF(DescribeShards(identity)).map(_.find(_.id.toLowerCase == id.trim.toLowerCase))

  ////////////// threading ///////////////

  import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService, ThreadFactory}

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  val serverPool: ExecutorService =
    Executors.newFixedThreadPool(2, daemonThreads("chemist-server"))

  val defaultPool: ExecutorService =
    Executors.newFixedThreadPool(8, daemonThreads("chemist-thread"))

  val schedulingPool: ScheduledExecutorService =
    Executors.newScheduledThreadPool(4, daemonThreads("chemist-scheduled-tasks"))
}

import java.io.File
import scalaz.==>>
import scalaz.concurrent.Task
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import oncue.svc.funnel.aws.{SQS,SNS,EC2,ASG}
import java.util.concurrent.atomic.AtomicReference

trait Server extends Interpreter[Server.ServerF] {
  import knobs._
  import journal.Logger
  import Server._

  implicit val log = Logger("chemist")

  /////// configuration resolution ////////

  val cfg: Config = (for {
    a <- knobs.loadImmutable(List(Required(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))))) or
         knobs.loadImmutable(List(Required(ClassPathResource("oncue/chemist.cfg"))))
    b <- knobs.aws.config
  } yield a ++ b).run

  val topic = cfg.require[String]("chemist.sns-topic-name")
  val queue = cfg.require[String]("chemist.sqs-queue-name")

  /////// queues and event streaming ////////

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

  val ec2 = EC2.client(
    new BasicAWSCredentials(
      cfg.require[String]("aws.access-key"),
      cfg.require[String]("aws.secret-key")),
    cfg.lookup[String]("aws.proxy-host"),
    cfg.lookup[Int]("aws.proxy-port"),
    cfg.lookup[String]("aws.proxy-protocol"),
    Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
  )

  val asg = ASG.client(
    new BasicAWSCredentials(
      cfg.require[String]("aws.access-key"),
      cfg.require[String]("aws.secret-key")),
    cfg.lookup[String]("aws.proxy-host"),
    cfg.lookup[Int]("aws.proxy-port"),
    cfg.lookup[String]("aws.proxy-protocol"),
    Region.getRegion(Regions.fromName(cfg.require[String]("aws.region")))
  )

  /////// in-memory data storage ////////

  val R = new StatefulRepository(ec2)

  /////// interpreter implementation ////////

  protected def op[A](r: ServerF[A]): Task[A] = r match {

    case DescribeDistribution(k) =>
      R.distribution.map(d => k(Sharding.snapshot(d)))

    case Distribute(targets, k) =>
      Task.now(k)

    case DescribeShards(k) =>
      for {
        a <- R.distribution.map(Sharding.shards)
        b <- Task.gatherUnordered(a.map(R.instance).toSeq)
      } yield k(b)

    // case Listen(k) =>
      // Lifecycle.run(queue, sqs, D).run.map(_ => k)
  }

  protected def init(): Task[Unit] =
    for {
      // read the list of all deployed machines
      z <- Deployed.list(asg, ec2)
      // set the result to an in-memory list of "the world"
      _ <- Task.gatherUnordered(z.map(R.addInstance))
      // from the whole world, figure out which are flask instances
      _ <- Task.gatherUnordered(z.filter(Deployed.filter.flasks).map(R.increaseCapacity))
      // ask those flasks for their current work and update the distribution accordingly
      _ <- Task.now(())
      // start to wire up the topics and subscriptions to queues
      // a <- SNS.create(topic)(sns)
      // _  = log.debug(s"created sns topic with arn = $a")
      // b <- SQS.create(queue)(sqs)
      // _  = log.debug(s"created sqs queue with arn = $a")
      // c <- SNS.subscribe(a, b)(sns)
      // _  = log.debug(s"subscribed sqs queue to the sns topic")
      // _ <- collateExistingWork
    } yield ()

}

object Server0 extends Server {
  init().runAsync(x => ())
}
