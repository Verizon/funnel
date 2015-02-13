package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.{Attach, MBeanQuery, RichMBeanServerConnection, JMXConnection}
import journal.Logger
import scalaz.stream.{Process,Channel,io}
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.std.option._
import javax.management.remote.JMXConnector
import javax.management.{MBeanServerConnection, ObjectName, Attribute, MBeanInfo}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

case class JMXImportException(override val getMessage: String) extends RuntimeException

object Testing {
  def main(args: Array[String]): Unit = {
    println {
      Import.remoteConnector("service:jmx:rmi:///jndi/rmi://127.0.0.1:8153/jmxrmi")(new ConcurrentHashMap)
        .map(_.getMBeanServerConnection)
        .map(c => {
          val query = MBeanQuery(new ObjectName("org.apache.ZooKeeperService:*"))
          Import.specific(query)(c).foreach(println)
          ()
        }).run
    }
  }
}

object Import {
  import JMXConnection._

  private[this] val log = Logger[Import.type]

  type ConnectorCache = ConcurrentHashMap[String, JMXConnector]

  /**
   * We dont want to constantly thrash making connections to other VMs on the same host
   * so instead we will keep some state around about the connectors that are already avalible
   * and use those instead. It's basically a cache to avoid needless re-evaluation.
   */
  def remoteConnector(location: String)(cache: ConnectorCache): Task[JMXConnector] =
    if(cache.containsKey(location)) Task.now(cache.get(location))
    else {
      for {
        a <- Attach.remoteConnect(location, credentials = None).fold(e => Task.fail(JMXImportException(e)), Task.now)
        _ <- Task.delay(cache.putIfAbsent(location, a))
      } yield a
    }

  def specific(query: MBeanQuery)(svr: MBeanServerConnection): Set[(ObjectName, Attribute)] = {
    val getAttributeNames: ObjectName => Set[(ObjectName,Attribute)] = { on =>
      val info = svr.getMBeanInfo(on).getAttributes.filter(_.isReadable)
      info.filter(_.isReadable).flatMap(attr => svr.attribute(on, attr.getName).map(on -> _)).toSet
    }
    svr.toScala.queryNames(query).flatMap(getAttributeNames)
  }

  def all(svr: MBeanServerConnection): Set[(ObjectName, Attribute)] =
    specific(MBeanQuery.All)(svr)

  def readableAttributeNames(svr: MBeanServerConnection, query: Option[MBeanQuery]): Set[String] = {
    val getAttributeNames: ObjectName => Set[String] = on => svr.getMBeanInfo(on).getAttributes.filter(_.isReadable).map { _.getName }.toSet
    query.cata(q => safely(Set.empty[String]){ svr.toScala.queryNames(q).flatMap(getAttributeNames) }, Set.empty[String])
  }

  private def safely[A](onError: => A)(f: => A): A =
    try f catch { case NonFatal(e) => onError }

  // def periodicly(from: Seq[VMID])(frequency: Duration = 10.seconds): Process[Task,Unit] =
// Process.awakeEvery(frequency)(Strategy.Executor(serverPool), schedulingPool
      // )


}
