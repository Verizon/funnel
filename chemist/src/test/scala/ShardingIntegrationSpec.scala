package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import intelmedia.ws.funnel.{Monitoring,Instruments,Clocks,JVM}
import intelmedia.ws.funnel.http.MonitoringServer
import scalaz.==>>
import concurrent.duration._

class ShardingIntegrationSpec extends FlatSpec with Matchers with BeforeAndAfterAll with ChemistSpec {
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
  val FS1 = MonitoringServer.start(F1, 9090)

  val instances = new Ref[InstanceM](==>>())
  val distribution = new Ref[Distribution](Distribution.empty)

  val T1 = Set(
    Target("test1",SafeURL("http://127.0.0.1:8080/stream/uptime")),
    Target("test1",SafeURL("http://127.0.0.1:8080/stream/now")),
    Target("test1",SafeURL("http://127.0.0.1:8081/stream/uptime")),
    Target("test1",SafeURL("http://127.0.0.1:8081/stream/now"))
  )

  override def beforeAll(){
    addInstruments(I3)
    addInstruments(I1)
    addInstruments(I2)
    Thread.sleep(100)
    addFlask("i-123", 9090)
    Thread.sleep(1000)
  }

  override def afterAll(){
    Thread.sleep(W.toMillis * 2) // wait for 2 window periods
    MS1.stop()
    MS2.stop()
    FS1.stop()
    dispatch.Http.shutdown()
  }

  private def addFlask(fid: String, fport: Int): Unit = {
    instances.update(_.insert(fid,
      Instance(
        id = fid,
        location = Location.localhost.copy(port = fport),
        firewalls = Seq.empty
      )
    ))
    // typically happens with asg lifecycle, but simulating here
    distribution.update(_.insert(fid, Set.empty))
  }

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  it should "sucsessfully be able to stream events from two local monitoring instances to a local flask" in {
    F1.processMirroringEvents(
      intelmedia.ws.funnel.http.SSE.readEvents,
      "intspec")(println).runAsync(println)

    sys.error("FIX ME")

    // Sharding.distribute(
    //     Sharding.distribution(T1)(distribution.get))(distribution,instances)
  }



}

