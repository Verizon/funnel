package funnel
package agent
package jmx

import concurrent.duration._
import cjmx.util.jmx.{MBeanQuery, RichMBeanServerConnection, JMX}
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import java.util.concurrent.ConcurrentHashMap
import org.apache.curator.test.TestingServer

import org.scalatest._

class PeriodicJMXTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  // This apparently doesn't provide RMI access to JMX
  val server = new TestingServer(5678, false)

  override def beforeAll {
    server.start()
  }

  override def afterAll {
    server.close()
  }

  "JMX Import.periodically()" should "not fail even when it can't connect to JMX RMI" in {
    val zookeeper = "service:jmx:rmi:///jndi/rmi://127.0.0.1:8153/jmxrmi"
    val zooquery = MBeanQuery(new ObjectName("org.apache.ZooKeeperService:*"))
    val cache = new ConcurrentHashMap[String, JMXConnector]
    val exclusions = (s: String) =>
      Glob("*HistogramMicros").matches(s) ||
      Glob("*Histogram").matches(s)
    val I = new Instruments(30.seconds, Monitoring.default)

    val s = Import.periodically(zookeeper, Vector(zooquery), exclusions, "TestCluster")(cache, I)()
    s.take(3).runLog.run should have length 3
  }
}
