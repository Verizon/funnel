package oncue.svc.funnel.chemist

import java.net.URL
import com.amazonaws.services.sqs.model.Message
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import scalaz.{~>,Free,Functor,\/,-\/,\/-}, Free.liftF
import Sharding.{Target,Distribution}

object Server {
  type Server[A] = Free[ServerF, A]

  ////////////// ServerF algebra //////////////

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
  case class Bootstrap[A](k: A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = Bootstrap(g(k))
  }
  case class AlterShard[A](id: InstanceID, state: AutoScalingEventKind, k: A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = AlterShard(id, state, g(k))
  }

  ////////////// free monad plumbing //////////////

  implicit def serverFFunctor[B]: Functor[ServerF] = new Functor[ServerF]{
    def map[A,B](fa: ServerF[A])(f: A => B): ServerF[B] = fa.map(f)
  }

  ////////////// public api / syntax ///////////////

  /**
   * Of all known monitorable services, dispaly the current work assignments
   * of funnel -> flask.
   */
  def distribution: Server[Map[InstanceID, Map[String, List[SafeURL]]]] =
    liftF(DescribeDistribution(identity))

  /**
   * manually ask chemist to assign the given urls to a flask in order
   * to be monitored. This is not recomended as a daily-use function; chemist
   * should be smart enough to figure out when things go on/offline automatically.
   */
  def distribute(urls: Set[Target]): Server[Unit] =
    liftF(Distribute(urls, ()))

  /**
   * list all the shards currently known by chemist.
   */
  def shards: Server[Seq[Instance]] =
    liftF(DescribeShards(identity))

  /**
   * display all known node information about a specific shard
   */
  def shard(id: InstanceID): Server[Option[Instance]] = {
    val ds: ServerF[Seq[Instance]] = DescribeShards(identity)
    liftF(ds).map(_.find(_.id.toLowerCase == id.trim.toLowerCase))
  }

  /**
   * Instruct flask to specifcally take a given shard out of service and
   * repartiion its given load to the rest of the system.
   */
  def exclude(shard: InstanceID): Server[Unit] =
    liftF(AlterShard(shard.toLowerCase, Terminate, ()))

  /**
   * Instruct flask to specifcally "launch" a given shard and
   * start sending new load to the "new" shard.
   *
   * NOTE: Assumes all added instances here are free of work already.
   */
  def include(shard: InstanceID): Server[Unit] =
    liftF(AlterShard(shard.toLowerCase, Launch, ()))

  /**
   * Force chemist to re-read the world from AWS. Useful if for some reason
   * Chemist gets into a weird state at runtime.
   */
  def bootstrap: Server[Unit] =
    liftF(Bootstrap(()))

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

  implicit lazy val log = Logger[Server.type]

  /////// configuration resolution ////////

  val cfg: Config = (for {
    a <- knobs.loadImmutable(List(Required(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))))) or
         knobs.loadImmutable(List(Required(ClassPathResource("oncue/chemist.cfg"))))
    b <- knobs.aws.config
  } yield a ++ b).run

  val topic     = cfg.require[String]("chemist.sns-topic-name")
  val queue     = cfg.require[String]("chemist.sqs-queue-name")
  val resources = cfg.require[List[String]]("chemist.resources-to-monitor")

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

    case AlterShard(id, s, k) =>
      for {
        _ <- Lifecycle.event(AutoScalingEvent(id, s), resources)(R, asg, ec2)
      } yield k

    case Bootstrap(k) =>
      Task.fork(bootstrap()).map(_ => k)
  }

  protected def bootstrap(): Task[Unit] =
    for {
      // read the list of all deployed machines
      l <- Deployed.list(asg, ec2)
      _  = log.info(s"found a total of ${l.length} deployed, accessable instances...")
      // filter out all the instances that are in private networks
      // TODO: support VPCs by dynamically determining if chemist is in a vpc itself
      z  = l.filterNot(x => x.location.isPrivateNetwork && Deployed.filter.flasks(x))
      _  = log.info(s"located ${z.length} instances that appear to be monitorable")

      // convert the instance list into reachable targets
      t  = z.flatMap(Target.fromInstance(resources)).toSet
      _  = log.debug(s"targets are: $t")

      // set the result to an in-memory list of "the world"
      _ <- Task.gatherUnordered(z.map(R.addInstance))
      _  = log.info("added instances to the repository...")

      // from the whole world, figure out which are flask instances
      f  = l.filter(Deployed.filter.flasks)
      _  = log.info(s"found ${f.length} flasks in the running instance list...")

      // update the distribution with new capacity seeds
      _ <- Task.gatherUnordered(f.map(R.increaseCapacity))
      _  = log.debug("increased the known monitoring capactiy based on discovered flasks")

      // ask those flasks for their current work and yield a `Distribution`
      d <- Sharding.gatherAssignedTargets(f)
      _  = log.debug("read the existing state of assigned work from the remote instances")

      // update the distribution accordingly
      _ <- R.mergeDistribution(d)
      _  = log.debug("merged the currently assigned work into the current distribution")

      _ <- for {
        h <- Sharding.locateAndAssignDistribution(t,R)
        g <- Sharding.distribute(h)
      } yield ()

      _ <- Task(log.info(">>>>>>>>>>>> boostrap complete <<<<<<<<<<<<"))
    } yield ()

  protected def init(): Task[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      _ <- bootstrap

      // start to wire up the topics and subscriptions to queues
      a <- SNS.create(topic)(sns)
      _  = log.debug(s"created sns topic with arn = $a")

      b <- SQS.create(queue)(sqs)
      _  = log.debug(s"created sqs queue with arn = $a")

      c <- SNS.subscribe(a, b)(sns)
      _  = log.debug(s"subscribed sqs queue to the sns topic")

      // now the queues are setup with the right permissions,
      // start the lifecycle listener
      _ <- Lifecycle.run(queue, resources, Lifecycle.sink)(R, sqs, asg, ec2)
      _  = log.debug("lifecycle process started")

      _ <- Task(log.info(">>>>>>>>>>>> bootup complete <<<<<<<<<<<<"))
    } yield ()
  }

}

object Server0 extends Server {
  init().runAsync(x => ())
}
