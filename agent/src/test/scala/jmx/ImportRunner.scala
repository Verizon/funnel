package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.{MBeanQuery, RichMBeanServerConnection, JMX}
import javax.management.remote.JMXConnector
import javax.management.ObjectName
import java.util.concurrent.ConcurrentHashMap

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

    Import.foo(cassandra, Vector(cassquery), exclusions)(cache).run.foreach(println)

  }
}