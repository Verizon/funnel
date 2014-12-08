package oncue.svc.funnel
package http

import java.net.URL
import scala.concurrent.duration._
import scalaz.stream.Process
import org.scalatest._, matchers.ShouldMatchers

class MirroringIntegrationSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  private def mirrorFrom(port: Int): Unit =
    MI.mirrorAll(SSE.readEvents)(
      new URL(s"http://localhost:$port/stream/previous"),
      Map("port" -> port.toString)
    ).run.runAsync(_ => ())

  implicit val log = (s: String) => { println(s) }

  val W = 30.seconds

  lazy val M1 = Monitoring.instance
  lazy val I1 = new Instruments(W, M1)

  lazy val M2 = Monitoring.instance
  lazy val I2 = new Instruments(W, M2)

  lazy val M3 = Monitoring.instance
  lazy val I3 = new Instruments(W, M3)

  val MS1 = MonitoringServer.start(M1, 3001)
  val MS2 = MonitoringServer.start(M2, 3002)
  val MS3 = MonitoringServer.start(M3, 3003)

  // mirror to this instance
  lazy val MI = Monitoring.instance

  val KMI = MonitoringServer.start(MI, 5775)

  override def beforeAll(){
    addInstruments(I1)
    addInstruments(I2)
    addInstruments(I3)

    Thread.sleep(2000)

    // shutdown all the servers
    mirrorFrom(3001)
    mirrorFrom(3002)
    mirrorFrom(3003)

    Thread.sleep(W.toMillis * 2)
  }

  override def afterAll(){
    // shutdown all the servers
    MS1.stop()
    MS2.stop()
    MS3.stop()
    KMI.stop()
  }

  // never do stuff like this in production code.
  // this is a dirty hack to make some assertions about
  // the monitoring instances
  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.filter(_.startsWith("previous")).length

  it should "be able to mirror all the keys from the other instances into the current state of 'MI'" in {
    (countKeys(M1) +
    countKeys(M2) +
    countKeys(M3)) should equal (MI.keys.compareAndSet(identity).run.get.length)
  }
}
