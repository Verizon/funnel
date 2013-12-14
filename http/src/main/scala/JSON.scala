package intelmedia.ws
package funnel

import argonaut.{DecodeResult => D, _}
import argonaut.EncodeJson.{
  BooleanEncodeJson, StringEncodeJson, Tuple2EncodeJson,
  jencode1, jencode2L, jencode3L, jencode4L, jencode6L, jencode7L
}
import argonaut.DecodeJson.jdecode6L
import argonaut.CodecJson.casecodec2

import java.io.InputStream
import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream._

/** 
  * Use this JSON construction to instruct the admin server of new URLs
  * that it should add to the incoming "mirror" stream.
  * 
  * ````
  * [
  *   {
  *     "bucket": "accounts",
  *     "urls": [
  *       "http://sdfsd.com/sdf",
  *       "http://improd.dfs/sdfsd"
  *     ]
  *   }
  * ]
  * ````
  **/ 
case class Bucket(label: String, urls: List[String])

/** JSON encoders and decoders for types in the this library. */
object JSON {

  val R = Reportable
  val kindF = "kind"

  def encodeResult[A](a: A)(implicit A: EncodeJson[A]): Json = A(a)
  def encode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).nospaces
  def prettyEncode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).spaces2

  implicit val BucketCodecJson: DecodeJson[Bucket] =
    casecodec2(Bucket.apply, Bucket.unapply)("bucket", "urls")

  implicit val DoubleEncodeJson =
    jencode1[Double,Option[Double]] {
      case d if d.isNaN || d.isInfinity => None
      case d => Some(d)
    } (argonaut.EncodeJson.OptionEncodeJson(argonaut.EncodeJson.DoubleEncodeJson))

  def decodeUnion[A](kindF: String)(cases: (String, DecodeJson[A])*): DecodeJson[A] = {
    val byKind = cases.toMap
    DecodeJson { c =>
      (c --\ kindF).as[String].flatMap { kind =>
        byKind.get(kind).map(ctor => ctor(c))
              .getOrElse(D.fail("unknown kind: " + kind, c.history))
      }
    }
  }

  // "HELLO" -> "Hello", "hello" -> "hello", "hELLO" -> "hello"
  def unCapsLock(s: String): String =
    if (s.isEmpty) s
    else s(0) + s.drop(1).toLowerCase

  implicit def EncodeUnits[A]: EncodeJson[Units[A]] = {
    import Units._; import Units.Base._
    jencode1[Units[A], String] {
      case Bytes(Zero) => "Bytes"
      case Bytes(Kilo) => "Kilobytes"
      case Bytes(Mega) => "Megabytes"
      case Bytes(Giga) => "Gigabytes"
      case Duration(g) => unCapsLock(g.toString)
      case Count => "Count"
      case Ratio => "Ratio"
      case TrafficLight => "TrafficLight"
      case Healthy => "Healthy"
      case Load => "Load"
      case None => "None"
    }
  }
  implicit def DecodeUnits: DecodeJson[Units[Any]] = DecodeJson { c =>
    import Units._; import Units.Base._
    c.as[String] flatMap {
      case "Bytes" => D.ok { Bytes(Zero) }
      case "Kilobytes" => D.ok { Bytes(Kilo) }
      case "Megabytes" => D.ok { Bytes(Mega) }
      case "Gigabytes" => D.ok { Bytes(Giga) }
      case "Count" => D.ok { Count }
      case "Ratio" => D.ok { Ratio }
      case "TrafficLight" => D.ok { TrafficLight }
      case "Healthy" => D.ok { Healthy }
      case "Load" => D.ok { Load }
      case "None" => D.ok { None }
      case timeunit =>
        try D.ok(Duration(TimeUnit.valueOf(timeunit.toUpperCase)))
        catch { case e: IllegalArgumentException =>
          D.fail("invalid units: " + timeunit, c.history)
        }
    }
  }

  implicit def EncodeKey[A]: EncodeJson[Key[A]] =
    jencode3L((k: Key[A]) => (k.name, k.typeOf, k.units))("name", "type", "units")
  implicit def DecodeKey: DecodeJson[Key[Any]] = DecodeJson { c => for {
    name   <- (c --\ "name").as[String]
    typeOf <- (c --\ "type").as[Reportable[Any]]
    u      <- (c --\ "units").as[Units[Any]]
  } yield Key(name, typeOf, u) }

  implicit def EncodeStats: EncodeJson[funnel.Stats] =
    jencode7L((s: funnel.Stats) =>
      (s.last, s.mean, s.count.toDouble, s.variance, s.standardDeviation, s.skewness, s.kurtosis))(
       "last", "mean", "count", "variance", "standardDeviation", "skewness", "kurtosis")
  implicit def DecodeStats: DecodeJson[funnel.Stats] =
    jdecode6L((last: Option[Double], mean: Double, count: Double, variance: Double,
               skewness: Double, kurtosis: Double) => {
      val m0 = count.toLong
      val m1 = mean
      // need to reverse the math to compute m2, m3, m4 from variance, skewness, kurtosis
      // see: https://github.com/twitter/algebird/blob/develop/algebird-core/src/main/scala/com/twitter/algebird/MomentsGroup.scala
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new funnel.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    })("last", "mean", "count", "variance", "skewness", "kurtosis")

  implicit def EncodeReportable[A:Reportable]: EncodeJson[A] = EncodeJson {
    case a: Double => encodeResult(a)
    case a: Boolean => encodeResult(a)
    case a: String => encodeResult(a)
    case a: funnel.Stats => encodeResult(a)
    case h => sys.error("unsupported reportable: " + h)
  }

  implicit def DecodeReportable(r: Reportable[Any]): DecodeJson[Any] = DecodeJson { c =>
    r match {
      case Reportable.B => c.as[Boolean] ||| D.fail("expected Boolean", c.history)
      case Reportable.D => c.as[Double] ||| D.fail("expected Double", c.history)
      case Reportable.Stats => c.as[funnel.Stats] ||| D.fail("expected Stats", c.history)
      case Reportable.S => c.as[String] ||| D.fail("expected String", c.history)
    }
  }

  implicit def EncodeReportableT[A]: EncodeJson[Reportable[A]] =
    jencode1((r: Reportable[A]) => r.description)

  implicit def DecodeReportableT: DecodeJson[Reportable[Any]] = DecodeJson { c =>
    c.as[String].flatMap { s =>
      Reportable.fromDescription(s).map(D.ok)
                .getOrElse(D.fail("invalid type: " + s, c.history))
    }
  }

  implicit def EncodeDatapoint[A]: EncodeJson[Datapoint[A]] =
    jencode2L((d: Datapoint[A]) => (d.key, EncodeReportable(d.key.typeOf)(d.value)))("key", "value")

  implicit def DecodeDatapoint: DecodeJson[Datapoint[Any]] = DecodeJson { c => for {
    k <- (c --\ "key").as[Key[Any]]
    v <- (c --\ "value").as[Any](DecodeReportable(k.typeOf))
  } yield Datapoint(k, v) }

}

