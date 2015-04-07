package funnel
package messages

import scalaz.concurrent._
import scalaz.syntax.traverse._
import scalaz.std.vector._
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

  val errors = List(
    Error(Names("kind1", "mine", "theirs")),
    Error(Names("kind2", "mine", "theirs")),
    Error(Names("kind3", "mine", "theirs")),
    Error(Names("kind4", "mine", "theirs"))
  )
}


class SpecMultiJvmPub extends FlatSpec with Matchers with TelemetryMultiTest {

  "publish socket" should "publish" in {
    S.set(true).run

    val keysIn = signalOf(Set.empty[Key[Any]])
    val keysInD = keysIn.discrete

    val sets: Vector[Set[Key[Any]]] = testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty)

    sets.traverse_{ s => keysIn.set(s) }.run

    Thread.sleep(100)

    val errorsS = Process.emitAll(errors)

    val pub: Task[Unit] = telemetryPublishSocket(U1, S, errorsS.wye(keysInD pipe keyChanges)(wye.merge))
    pub.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) => println("pub runasync success")
    }

    Thread.sleep(100)
    keysIn.close.run
  }
}

class SpecMultiJvmSub extends FlatSpec with Matchers with TelemetryMultiTest {

  "sub socket" should "sub" in {
    val (keysout, errorsS, sub) = telemetrySubscribeSocket(U1, S)
    val keysoutS = keysout.discrete

    sub.run.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) => println("sub runasync success")
    }

    var keysOut: Set[Key[Any]] = Set.empty
    val myAwesomeSink: Sink[Task,Set[Key[Any]]] = Process.constant { k =>
      Task {
        keysOut = k
      }
    }

    var errorsOut: List[Error] = Nil
    val anotherAwesomeSink: Sink[Task,Error] = Process.constant { e =>
      Task {
        errorsOut = e :: errorsOut
      }
    }



    (keysoutS to myAwesomeSink).run.runAsync(_ => ())
    (errorsS to anotherAwesomeSink).run.runAsync(_ => ())

    Thread.sleep(100)
    keysout.close.run
    S.set(false).run

    keysOut should be (testKeys.toSet)
    errorsOut.reverse should be (errors)
  }

}

