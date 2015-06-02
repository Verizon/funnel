package funnel
package zeromq

import java.net.URI
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.stream.{Channel,Process,io,async}
import scalaz.stream.async.mutable.Queue
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import sockets._
import scala.concurrent.duration._

class MirrorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  lazy val S  = async.signalOf[Boolean](true)(Strategy.Executor(Monitoring.serverPool))
  lazy val W  = 20.seconds

  lazy val Q: Queue[Telemetry] = async.unboundedQueue(Strategy.Executor(Monitoring.serverPool))

  lazy val U1 = new URI("ipc:///tmp/u1.socket")
  lazy val E1 = Endpoint.unsafeApply(publish &&& bind, U1)
  lazy val M1 = Monitoring.instance
  lazy val I1 = new Instruments(W, M1)

  lazy val U2 = new URI("ipc:///tmp/u2.socket")
  lazy val E2 = Endpoint.unsafeApply(publish &&& bind, U2)
  lazy val M2 = Monitoring.instance
  lazy val I2 = new Instruments(W, M2)

  // mirror to this instance
  lazy val MI = Monitoring.instance

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.filter(_.startsWith("previous")).size

  private def mirrorFrom(uri: URI): Unit =
    MI.mirrorAll(Mirror.from(S, Q)
      )(uri, Map("uri" -> uri.toString)
      ).run.runAsync(_.fold(e => Ø.log.error(
        s"Error mirroring $uri: ${e.getMessage}"), identity))

  override def beforeAll(){
    if(Ø.isEnabled){
      addInstruments(I1)
      addInstruments(I2)

      Publish.to(E1)(S,M1)
      Publish.to(E2)(S,M2)

      Thread.sleep(2.seconds.toMillis)

      mirrorFrom(U1)
      mirrorFrom(U2)

      Thread.sleep(W.toMillis*2)
    }
  }

  override def afterAll(){
    stop(S).run
  }

  if(Ø.isEnabled){
    "zeromq mirroring" should "pull values from the specified monitoring instance" in {
      (countKeys(M1) + countKeys(M2)) should equal (MI.keys.get.run.size)
      MI.keys.get.run.size should be > 0
    }
  }
}
