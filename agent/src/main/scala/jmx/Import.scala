package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.{Attach, MBeanQuery, RichMBeanServerConnection, JMXConnection, JMX}
import journal.Logger
import scalaz.stream.{Process,Channel,io}
import scalaz.concurrent.{Task,Strategy}
import scalaz.std.option._
import scalaz.syntax.std.option._
import javax.management.remote.JMXConnector
import javax.management.{MBeanServerConnection, ObjectName, Attribute, MBeanInfo}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.concurrent.duration._

case class JMXImportException(override val getMessage: String) extends RuntimeException

object Import {
  import JMXConnection._
  import Monitoring.{serverPool,schedulingPool}

  private[this] val log = Logger[Import.type]

  type ConnectorCache = ConcurrentHashMap[String, JMXConnector]

  def retrieve(
    location: String,
    queries: Seq[MBeanQuery],
    exclusions: String => Boolean
  )(cache: ConnectorCache
  ): Task[Seq[ArbitraryMetric]] = {
    log.debug("Retrieving metrics from the JMX endpoint...")
    remoteConnector(location)(cache).map(connector =>
      queries.flatMap(q => specific(q)(connector.mbeanServer))
      .filterNot { case (a,b) => exclusions(JMX.humanizeKey(b.getName)) }
      .flatMap { case (a,b) => toArbitraryMetric(a,b) })
  }

  def toArbitraryMetric(obj: ObjectName, attribute: Attribute): Option[ArbitraryMetric] = {
    import InstrumentKinds._
    for {
      n <- Parser.parse(obj.getCanonicalName, JMX.humanizeKey(attribute.getName)).toOption
      v <- Option(attribute.getValue)
      k  = v.getClass.getSimpleName.toLowerCase
    } yield {
      val kind = k match {
        case "double" | "long" | "integer" => GaugeDouble
        case _                             => GaugeString
      }
      ArbitraryMetric(n, kind, Option(JMX.humanizeValue(v)))
    }
  }

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

  def periodicly(
    location: String,
    queries: Seq[MBeanQuery],
    exclusions: String => Boolean
  )(cache: ConnectorCache
  )(frequency: Duration = 10.seconds
  ): Process[Task,Unit] =
    Process.awakeEvery(frequency)(Strategy.Executor(serverPool), schedulingPool)
      .evalMap { _ =>
        println("==============================")
      for {
        a <- retrieve(location, queries, exclusions)(cache)
        _ <- Task.now(())
      } yield ()
    }

}
