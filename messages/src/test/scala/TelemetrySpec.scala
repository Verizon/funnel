package funnel
package messages

import org.scalacheck._
import Arbitrary._
import org.scalacheck.Prop.forAll
import shapeless.contrib.scalacheck._
import scalaz.concurrent._
import scalaz.stream.async._
import scalaz.stream.{Process,Channel,io, Sink}
import scalaz.std.anyVal._
import java.net.URI
import Telemetry._
import zeromq._

object Spec extends Properties("") {
  val S  = signalOf[Boolean](true)
  val U1 = new URI("ipc:///tmp/u1.socket")

  val reportableGen = {
    import Reportable._
    Gen.oneOf(B,D,Reportable.S,Stats)
  }

  val timeUnitGen = {
    import java.util.concurrent.TimeUnit
    import TimeUnit._

    Gen.oneOf(DAYS, HOURS, MICROSECONDS, MILLISECONDS, MINUTES, NANOSECONDS, SECONDS)
  }
  implicit val arbitraryTimeUnit = Arbitrary(timeUnitGen)

  val durationGen = timeUnitGen map Units.Duration

  val baseGen: Gen[Units.Base] = {
    import Units.Base._
    Gen.oneOf(Zero, Kilo, Mega, Giga)
  }

  val unitsGen : Gen[Units[Any]] = {
    import Units._
    Gen.oneOf(
      durationGen,
      baseGen.map(Bytes),
      Gen.const(Count),
      Gen.const(Ratio),
      Gen.const(TrafficLight),
      Gen.const(Healthy),
      Gen.const(Load),
      Gen.const(None)
    )
  }

  val keyGen: Gen[Key[Any]] = for {
    name <- arbitrary[String]
    typeOf <- reportableGen
    units <- unitsGen
    description <- arbitrary[String]
    attributes <- arbitrary[Map[String,String]]
  } yield Key(name, typeOf, units, description, attributes)

  implicit val arbKey: Arbitrary[Key[Any]] = Arbitrary(keyGen)

  property("property") = forAll {(testKeys: Vector[Key[Any]]) ⇒
    S.set(true).run

    val pub: Channel[Task,Telemetry, Boolean] = telemetryPublishSocket(U1, S)
    val keysIn = signalOf(Set.empty[Key[Any]])
    val (keysout,errors) = telemetrySubscribeSocket(U1, S)
    val keysoutS = keysout.discrete
    val sets: Vector[Set[Key[Any]]] = testKeys.tails.toVector.reverse.map(_.toSet).filterNot(_.isEmpty)

    println("PUB: " + pub)

    val log: Sink[Task,Telemetry] = Process.constant { t =>
      Task(println("observe: " + t.toString))
    }
    val log2: Sink[Task,Any] = Process.constant { t =>
      Task(println("outputoutputoutputoutput: " + t.toString))
    }
    sets.foreach { s =>
      println("setting: " + s)
      keysIn.set(s).run
    }
    val keysInD = keysIn.discrete

    (keysInD.pipe(keyChanges).observe(log).through(pub).to(log2)).run.runAsync(x => println("runasync output: " + x))

    S.set(false).run
    keysIn.close.run
    keysout.close.run
    val out = keysoutS.runLog.run
    println("in: " + sets)
    println("out: " + out)
    val x = out == sets

    println("x: " + x)
    x

  }
}
