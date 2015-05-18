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

  val unitsGen : Gen[Units] = {
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
    name        <- arbitrary[String]
    typeOf      <- reportableGen
    units       <- unitsGen
    description <- arbitrary[String]
    attributes  <- arbitrary[Map[String,String]]
    init        <- dataGen(typeOf)
  } yield Key(name, typeOf, units, description, attributes, init)

  implicit val arbKey: Arbitrary[Key[Any]] = Arbitrary(keyGen)


  val statsGen: Gen[Stats] =
    for {
      last <- arbitrary[Option[Double]]
      mean <- arbitrary[Double]
      count <- arbitrary[Int] suchThat (_ != 0)
      variance <- arbitrary[Double]
      skewness <- arbitrary[Double]
      kurtosis <- arbitrary[Double]
    } yield {
      val m0 = count.toLong
      val m1 = mean
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new funnel.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    }

  def dataGen[A](r: Reportable[A]): Gen[A] =
    r match {
      case Reportable.B => arbitrary[Boolean]
      case Reportable.D => arbitrary[Double]
      case Reportable.S => arbitrary[String]
      case Reportable.Stats => statsGen
    }

  val datapointGen: Gen[Datapoint[Any]] = for {
    k <- keyGen
    v <- dataGen(k.typeOf)
  } yield Datapoint(k, v)

}
