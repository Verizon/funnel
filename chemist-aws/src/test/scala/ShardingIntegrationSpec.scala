package funnel
package chemist
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import funnel.{Monitoring,Instruments,Clocks,JVM}
import funnel.http.MonitoringServer
import scalaz.==>>
import concurrent.duration._

class ShardingIntegrationSpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  import Sharding.{Distribution,Target}

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
  val D = new Discovery(E,A)
  val R = new StatefulRepository(D)



  val T1 = Set(
    Target("test1",SafeURL("http://127.0.0.1:8080/stream/uptime")),
    Target("test1",SafeURL("http://127.0.0.1:8080/stream/now")),
    Target("test1",SafeURL("http://127.0.0.1:8081/stream/uptime")),
    Target("test1",SafeURL("http://127.0.0.1:8081/stream/now"))
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
    R.increaseCapacity(fid).run
  }

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  it should "sucsessfully be able to stream events from two local monitoring instances to a local flask" in {
    F1.processMirroringEvents(
      funnel.http.SSE.readEvents,
      "intspec").runAsync(println)

    val x = for {
      a <- Sharding.locateAndAssignDistribution(T1,R)
      b <- Sharding.distribute(a)(H)
    } yield b

    x.run
  }



}

