package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.{Attach, MBeanQuery, RichMBeanServerConnection, JMXConnection, JMX}
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
    val zookeeper = "service:jmx:rmi:///jndi/rmi://127.0.0.1:8153/jmxrmi"
    val zooquery = MBeanQuery(new ObjectName("org.apache.ZooKeeperService:*"))

    val cassandra = "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi"
    val cassquery = MBeanQuery(new ObjectName("org.apache.cassandra.db:*"))

    val cache = new ConcurrentHashMap[String, JMXConnector]

    val exclusions = (s: String) =>
      Glob("*HistogramMicros").matches(s) ||
      Glob("*Histogram").matches(s)

    Import.foo(cassandra, Vector(cassquery), exclusions)(cache).run

    // println {
    //   Import.remoteConnector("service:jmx:rmi:///jndi/rmi://127.0.0.1:8153/jmxrmi")(new ConcurrentHashMap)
    //     .map(_.getMBeanServerConnection)
    //     .map(c => {
    //       val query = MBeanQuery(new ObjectName("org.apache.ZooKeeperService:*"))
    //       Import.specific(query)(c).foreach(println)
    //       ()
    //     }).run
    // }
  }
}

object Import {
  import JMXConnection._

  private[this] val log = Logger[Import.type]

  type ConnectorCache = ConcurrentHashMap[String, JMXConnector]

  def foo(location: String, queries: Seq[MBeanQuery], exclusions: String => Boolean)(cache: ConnectorCache) =
    remoteConnector(location)(cache).map(connector =>
      queries.flatMap(q => specific(q)(connector.mbeanServer))
      .filterNot { case (a,b) => exclusions(JMX.humanizeKey(b.getName)) }
      .flatMap { case (a,b) => toArbitraryMetric(a,b) })

  def toArbitraryMetric(obj: ObjectName, attribute: Attribute): Option[ArbitraryMetric] = {

    val name = Parser.parse(obj.getCanonicalName, attribute.getName)

    println(name + " = " + JMX.humanizeValue(attribute.getValue))


    // val kind = klass match {
    //   case _: javax.management.openmbean.TabularDataSupport => None
    //   case _: =>
    // }


    // .getClass match {
    //   case _: java.lang.Boolean =>
    //   case _:  =>
    //   case _:  =>
    //   case _:  =>
    //   case _:  =>
    //   case _:  =>
    //   case _:  =>
    // } catch {
    //   case e: Throwable => ()
    // }

    // println(s"name = $name, class = ${attribute.getValue.getClass}")

    None
  }

  case class JMXMetric(
    obj: ObjectName,
    info: MBeanInfo,
    attribute: Attribute)

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
      val info = svr.getMBeanInfo(on).getAttributes
      info.filter(_.isReadable)
          .flatMap(attr => svr.attribute(on, attr.getName)
          .map(on -> _)).toSet
    }
    svr.toScala.queryNames(query).flatMap(getAttributeNames)
  }

  def all(svr: MBeanServerConnection): Set[(ObjectName, Attribute)] =
    specific(MBeanQuery.All)(svr)

  def readableAttributeNames(svr: MBeanServerConnection, query: Option[MBeanQuery]): Set[String] = {
    val getAttributeNames: ObjectName => Set[String] = on =>
      svr.getMBeanInfo(on).getAttributes.filter(_.isReadable).map { _.getName }.toSet
    query.cata(q => safely(Set.empty[String]){
      svr.toScala.queryNames(q).flatMap(getAttributeNames) }, Set.empty[String])
  }

  private[this] def safely[A](onError: => A)(f: => A): A =
    try f catch { case NonFatal(e) => onError }

  // def periodicly(from: Seq[VMID])(frequency: Duration = 10.seconds): Process[Task,Unit] =
    // Process.awakeEvery(frequency)(Strategy.Executor(serverPool), schedulingPool)


}
