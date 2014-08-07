package oncue.svc.funnel.chemist

import scalaz.{~>,Free,Functor,\/,-\/,\/-}, Free.Return, Free.Suspend
import java.net.URL

object Server {
  type Server[A] = Free[ServerF, A]

  // ServerF algebra
  sealed trait ServerF[+A]{
    def map[B](f: A => B): ServerF[B]
  }

  case class Watch[A](urls: Set[URL], k: A) extends ServerF[A]{
    def map[B](g: A => B): ServerF[B] = Watch(urls, g(k))
  }

  /////// free monad plumbing ///////
  private def liftF[A](srv: ServerF[A]): Free[ServerF,A] =
    Suspend[ServerF, A](Functor[ServerF].map(srv)(a => Return[ServerF, A](a)))

  private implicit def serverFFunctor[B]: Functor[ServerF] = new Functor[ServerF]{
    def map[A,B](fa: ServerF[A])(f: A => B): ServerF[B] = fa.map(f)
  }

  /////// public api / syntax ////////

  def watch(urls: Set[URL]): Server[Unit] =
    liftF(Watch(urls, ()))
}

import java.io.File
import scalaz.concurrent.Task
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import com.amazonaws.services.sns.AmazonSNSClient
import com.amazonaws.services.sqs.AmazonSQSClient
import oncue.svc.funnel.aws.{SQS,SNS}

trait Server extends Interpreter[Server.ServerF] {
  import knobs._
  import Server._

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

  protected def op[A](r: ServerF[A]): Task[A] = r match {
    case Watch(urls, k) => Task.now( k )
  }

  protected def init(): Task[Unit] =
    Operations.setup(
      cfg.require[String]("chemist.sns-topic-name"),
      cfg.require[String]("chemist.sqs-queue-name"))(sns,sqs)
}

object Server0 extends Server {
  init().runAsync(x => ())
}
