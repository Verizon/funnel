package funnel
package telemetry

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

  val dummyActor: Actor[Any] = Actor { _ => () }

  val testKeys = List(
    Key("key1", Reportable.B, Units.Count, "desc", Map("ka1" -> "va1")),
    Key("key2", Reportable.D, Units.Ratio, "desc", Map("kb1" -> "vb1")),
    Key("key3", Reportable.S, Units.TrafficLight, "desc", Map("kc1" -> "vc1")),
    Key("key4", Reportable.B, Units.Load, "desc", Map("kd1" -> "vd1")),
    Key("key5", Reportable.D, Units.Count, "desc", Map("ke1" -> "ve1")),
    Key("key6", Reportable.S, Units.TrafficLight, "desc", Map("kf1" -> "vf1"))
  )

  val errors = List(
    Error(Names("kind1", "mine", new URI("http://theirs"))),
    Error(Names("kind2", "mine", new URI("http://theirs"))),
    Error(Names("kind3", "mine", new URI("http://theirs"))),
    Error(Names("kind4", "mine", new URI("http://theirs")))
  )
}


class SpecMultiJvmPub extends FlatSpec with Matchers with TelemetryMultiTest {

  "publish socket" should "publish" in {
    S.set(true).run

    val keysIn = signalOf(Set.empty[Key[Any]])
    val keysInD = keysIn.discrete

    val sets: Vector[Set[Key[Any]]] = testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty)

    sets.traverse_{ s => keysIn.set(s) }.run

    val errorsS = Process.emitAll(errors)

    val pub: Task[Unit] = telemetryPublishSocket(U1, S, errorsS.wye(keysInD pipe keyChanges)(wye.merge))
    pub.runAsync {
      case -\/(e) => e.printStackTrace
      case \/-(_) =>
    }

    Thread.sleep(200)
    keysIn.close.run
    Thread.sleep(200)
  }
}

class SpecMultiJvmSub extends FlatSpec with Matchers with TelemetryMultiTest {
  "sub socket" should "sub" in {

    var keysOut: Map[URI, Set[Key[Any]]] = Map.empty
    val keyActor: Actor[(URI, Set[Key[Any]])] = Actor {
      case (uri,keys) => keysOut = keysOut + (uri -> keys)
    }

    var errorsOut: List[Error] = List.empty
    val errorsActor: Actor[Error] = Actor(e => errorsOut = e :: errorsOut)

    val sub = telemetrySubscribeSocket(U1, S,
                                       keyActor,
                                       errorsActor,
                                       dummyActor.asInstanceOf[Actor[URI\/URI]])

    sub.runAsync(x => println("RESULT OF RUNNING TELEMETRY: " + x))

    Thread.sleep(10000)

    keysOut.size should be (1)
    keysOut(U1) should be (testKeys.toSet)

    errorsOut.reverse should be (errors)
  }

}

