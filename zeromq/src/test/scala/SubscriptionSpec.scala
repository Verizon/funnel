package funnel
package zeromq

import java.net.URI
import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scalaz.stream.async.signalOf
import scala.concurrent.duration._
import sockets._
import Publish.transportDatapoint
import scalaz.{-\/,\/,\/-}

class SubscriptionSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  lazy val S  = signalOf[Boolean](true)
  lazy val W  = 30.seconds

  lazy val U1 = new URI("ipc:///tmp/u1.socket")
  lazy val E1 = Endpoint.unsafeApply(publish &&& bind, U1)
  lazy val M1 = Monitoring.instance
  lazy val I1 = new Instruments(W, M1)

  lazy val U2 = new URI("ipc:///tmp/u2.socket")
  lazy val E2 = Endpoint.unsafeApply(publish &&& bind, U2)
  lazy val M2 = Monitoring.instance
  lazy val I2 = new Instruments(W, M2)

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  // mirror now to this instance
  lazy val MN = Monitoring.instance

  // mirror previous to this instance
  lazy val MP = Monitoring.instance

  override def beforeAll(){
    addInstruments(I1)
    addInstruments(I2)

    Ø.link(E1)(S)(socket =>
      Monitoring.subscribe(M1)(_ => true)
        .through(ZeroMQ.write(socket))
        .onComplete(scalaz.stream.Process.eval(stop(S)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        println(s"Unable to stream monitoring events to the socket ${E1.location.uri}")
        println(s"Error was: $err")

      case \/-(win) =>
        println(s"Streaming monitoring datapoints to the socket at ${E1.location.uri}")
    })

    Ø.link(E2)(S)(socket =>
      Monitoring.subscribe(M2)(_ => true)
        .through(ZeroMQ.write(socket))
        .onComplete(scalaz.stream.Process.eval(stop(S)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        println(s"Unable to stream monitoring events to the socket ${E2.location.uri}")
        println(s"Error was: $err")

      case \/-(win) =>
        println(s"Streaming monitoring datapoints to the socket at ${E2.location.uri}")
    })

    Thread.sleep(2.seconds.toMillis)

    mirror(U1, MN, List(nowCounter))
    mirror(U2, MP, List(previous))

    Thread.sleep(W.toMillis*2)
  }

  private def mirror(uri: URI, to: Monitoring, discriminator: List[Array[Byte]]): Unit =
    to.mirrorAll(Mirror.from(S, discriminator)
    )(uri, Map("uri" -> uri.toString)
    ).run.runAsync(_.fold(e => Ø.log.error(
                            s"Error mirroring $uri: ${e.getMessage}"), identity))


  override def afterAll(){
    stop(S).run
  }

  val previous = Transported(Schemes.fsm, Versions.v1, Some(Windows.previous), None, "".getBytes).header.getBytes("ASCII")
  val nowCounter = Transported(Schemes.fsm, Versions.v1, Some(Windows.now), Some(Topic("numeric")), "".getBytes).header.getBytes("ASCII")

  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.size

  if(Ø.isEnabled){
    "Subscription" should "discriminate" in {
      val cn = countKeys(MN)
      val cp = countKeys(MP)
      val c1 = M1.keys.get.run.size
      val c2 = M2.keys.get.run.size
      c1 should be > cn
      c2 should be > cp

//      println(s"n: $cn, p: $cp, c1: $c1, c2: $c2")

      cn should be > 0
      cp should be > 0
    }
  }
}
