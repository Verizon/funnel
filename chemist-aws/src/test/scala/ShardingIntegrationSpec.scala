package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import funnel.{Monitoring,Instruments,Clocks,JVM}
import funnel.http.MonitoringServer
import scalaz.==>>
import concurrent.duration._
import java.net.URI
import scalaz.concurrent.Strategy

class ShardingIntegrationSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  import Sharding.Distribution

  val W = 30.seconds

  /////////////////// targets ///////////////////
  lazy val M1 = Monitoring.instance
  lazy val I1 = new Instruments(W, M1)
  val MS1 = MonitoringServer.start(M1, 8080)

  lazy val M2 = Monitoring.instance
  lazy val I2 = new Instruments(W, M2)
  val MS2 = MonitoringServer.start(M1, 8081)

  /////////////////// flasks ///////////////////
  lazy val F1 = Monitoring.instance
  lazy val I3 = new Instruments(W, F1)
  val FS1 = MonitoringServer.start(F1, 5775)

  val E = TestAmazonEC2(Fixtures.instance(id = "i-localhost9090", publicDns = "localhost"))
  val A = TestAmazonASG.single(_ => java.util.UUID.randomUUID.toString)
//  val D = new AwsDiscovery(E,A)
  val R = new StatefulRepository



  val T1 = Set(
    Target("test1",new URI("http://127.0.0.1:8080/stream/uptime"), false),
    Target("test1",new URI("http://127.0.0.1:8080/stream/now"), false),
    Target("test1",new URI("http://127.0.0.1:8081/stream/uptime"), false),
    Target("test1",new URI("http://127.0.0.1:8081/stream/now"), false)
  )

  val H = dispatch.Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(5000))

  override def beforeAll(){
    addInstruments(I3)
    addInstruments(I1)
    addInstruments(I2)
    Thread.sleep(100)
    addFlask("i-localhost9090")
    Thread.sleep(1000)
  }

  override def afterAll(){
    Thread.sleep(W.toMillis * 2) // wait for 2 window periods
    MS1.stop()
    MS2.stop()
    FS1.stop()
    H.shutdown()
  }

  private def addFlask(fid: String): Unit = {
  }

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }
///* STU todo what is this actually testing?

  it should "sucsessfully be able to stream events from two local monitoring instances to a local flask" in {
    val Q = scalaz.stream.async.unboundedQueue[Telemetry](Strategy.Executor(Chemist.serverPool))
    F1.processMirroringEvents(
      funnel.http.SSE.readEvents(_,Q),
      Q,
      "intspec").runAsync(println)

    Thread.sleep(10000)

//    val x = for {
//      a <- Sharding.locateAndAssignDistribution(T1,R)
//      b <- Sharding.distribute(a)(H)
//    } yield b

//    x.run
  }

// */

}

