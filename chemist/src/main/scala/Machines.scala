package oncue.svc.funnel.chemist

import java.net.URL
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.stream.Process
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import oncue.svc.funnel.aws._

object Machines {
  private val defaultResources = Set(
    "stream/previous",
    "stream/sliding",
    "stream/uptime"
  )

  def listAll(asg: AmazonAutoScaling, ec2: AmazonEC2, resources: Set[String] = defaultResources): Task[Map[String, Seq[URL]]] =
    instances(g => true)(asg,ec2).map(urlsForInstances(_, resources))

  def listFunnels(asg: AmazonAutoScaling, ec2: AmazonEC2, resources: Set[String] = defaultResources): Task[Map[String, Seq[URL]]] =
    instances(filter.funnels)(asg,ec2).map(urlsForInstances(_, resources))

  def listFlasks(asg: AmazonAutoScaling, ec2: AmazonEC2, resources: Set[String] = defaultResources): Task[Map[String, Seq[URL]]] =
    instances(filter.flasks)(asg,ec2).map(urlsForInstances(_, resources))

  def urlsForInstances(m: Map[String, Seq[Instance]], resources: Set[String]): Map[String,Seq[URL]] =
    m.map {
      case (k,v) => k -> v.flatMap(i => resources.flatMap(p => i.asURL(path = p).toList ))
    }

  private def instances(f: Group => Boolean
    )(asg: AmazonAutoScaling, ec2: AmazonEC2
    ): Task[Map[String, Seq[Instance]]] =
    for {
      a <- readAutoScallingGroups(asg, ec2)
      // apply the specified filter if we want to remove specific groups for a reason
      x  = a.filter(f)
      // actually reach out to all the discovered hosts and check that their port is reachable
      y  = x.map(g => checkGroupInstances(g).map(g.bucket -> _))
      // run the tasks on the specified thread pool (Server.defaultPool)
      b <- Task.gatherUnordered(y)
    } yield b.filter(_._2.nonEmpty).toMap

  /**
   * This is kind of horrible, but it is what it is. The AWS api's really do not help here at all.
   * Sorry!
   */
  private def readAutoScallingGroups(asg: AmazonAutoScaling, ec2: AmazonEC2): Task[Seq[Group]] =
    for {
      g <- ASG.list(asg)
      x <- EC2.reservations(g.flatMap(_.instances.map(_.id)))(ec2)
    } yield {
      val instances: Map[String, AWSInstance] = x.flatMap(_.getInstances.asScala.toList)
                      .groupBy(_.getInstanceId).mapValues(_.head)

      g.map { grp =>
        grp.copy(
          instances = grp.instances.map { i =>
            val found = instances.get(i.id)
            val sgs = found.toList.flatMap(_.getSecurityGroups.asScala.toList).map(_.getGroupName)
            i.copy(
              internalHostname = found.map(_.getPrivateDnsName),
              // serioulsy hate APIs that return emtpy string as their result.
              externalHostname = found.flatMap(x => if(x.getPublicDnsName.nonEmpty) Option(x.getPublicDnsName) else None),
              securityGroups   = sgs
            )
          }.toList
        )
      }
    }

  object filter {
    def funnels(g: Group): Boolean =
      g.securityGroups.exists(_.toLowerCase == "monitor-funnel")

    def flasks(g: Group): Boolean =
      g.application.map(_.startsWith("flask")).getOrElse(false)

    def chemists(g: Group): Boolean =
      g.application.map(_.startsWith("chemist")).getOrElse(false)
  }

  // def periodic(delay: Duration)(asg: AmazonAutoScaling) =
  //   Process.awakeEvery(delay).evalMap(_ => list(asg))

  import scala.io.Source
  import scalaz.\/

  private def fetch(url: URL): Throwable \/ Unit =
    \/.fromTryCatch {
      val c = url.openConnection
      c.setConnectTimeout(300) // timeout in 300ms to keep the overhead reasonable
      c.setReadTimeout(300)
      c.connect()
    }

  /**
   * Goal of this function is to validate that the machine instances specified
   * by the supplied group `g`, are in fact running a funnel instance and it is
   * ready to start sending metrics if we connect to its `/stream` function.
   */
  def checkGroupInstances(g: Group): Task[List[Instance]] = {
    val fetches: Seq[Task[Throwable \/ Instance]] = g.instances.map { i =>
      (for {
        a <- Task(i.asURL.flatMap(fetch))(Server.defaultPool)
        _  = println(":::::  " + a)
        b <- a.fold(e => Task.fail(e), o => Task.now(o))
      } yield i).attempt
    }

    def discardProblematicInstances(l: List[Throwable \/ Instance]): List[Instance] =
      l.foldLeft(List.empty[Instance]){ (a,b) =>
        b.fold(e => a, i => a :+ i)
      }

    Task.gatherUnordered(fetches).map(discardProblematicInstances(_))
  }
}
