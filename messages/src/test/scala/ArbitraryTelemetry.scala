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

trait ArbitraryTelemetry {
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

}
