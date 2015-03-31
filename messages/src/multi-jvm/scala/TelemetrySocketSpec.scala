package funnel
package messages

import scalaz.concurrent._
import scalaz.stream.async._
import scalaz.stream.{Process,Channel,io, Sink, wye}
import scalaz.std.anyVal._
import java.net.URI
import scalaz.{-\/,\/,\/-}
import Telemetry._
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

trait TelemetryMultiTest {
  val S  = signalOf[Boolean](true)
  val U1 = new URI("ipc:///tmp/u1.socket")

  val testKeys = List(
    Key("key1", Reportable.B, Units.Count, "desc", Map("ka1" -> "va1")),
    Key("key2", Reportable.D, Units.Ratio, "desc", Map("kb1" -> "vb1")),
    Key("key3", Reportable.S, Units.TrafficLight, "desc", Map("kc1" -> "vc1")),
    Key("key4", Reportable.B, Units.Load, "desc", Map("kd1" -> "vd1")),
    Key("key5", Reportable.D, Units.Count, "desc", Map("ke1" -> "ve1")),
    Key("key6", Reportable.S, Units.TrafficLight, "desc", Map("kf1" -> "vf1"))
  )
}


class SpecMultiJvmPub extends FlatSpec with Matchers with TelemetryMultiTest {



  "publish socket" should "publish" in {
    S.set(true).run

    val keysIn = signalOf(Set.empty[Key[Any]])
    val keysInD = keysIn.discrete

    val sets: Vector[Set[Key[Any]]] = testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty)

    sets.foreach { s =>
      (for {
        _ <- Task.delay(println("adding keys to signal: " + s))
        _ <- keysIn.set(s)
      } yield ()).run
    }

    val pub: Task[Unit] = telemetryPublishSocket(U1, S, (keysInD pipe keyChanges))

    pub.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) => println("pub runasync success")
    }
    
    Thread.sleep(2000)
    keysIn.close.run
    Thread.sleep(2000)
//    S.set(false).run

    println("in: " + sets)

  }
}

class SpecMultiJvmSub extends FlatSpec with Matchers with TelemetryMultiTest {

  "sub socket" should "sub" in {
    val (keysout, errors, sub) = telemetrySubscribeSocket(U1, S)
    val keysoutS = S.discrete.wye(keysout.discrete)(wye.interrupt)
    sub.run.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) => println("sub runasync success")
    }
    
    val out: Task[IndexedSeq[Set[Key[Any]]]] = Task.async { cb =>
      keysoutS.runLog.runAsync(cb)
    }

    println("a")
    Thread.sleep(10000)
    println("b")
    S.set(false).run
    println("c")

    val keys = out.run
    println("d")
    println("out: " + keys)

    keys should be (testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty))
  }

}

