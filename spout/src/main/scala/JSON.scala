package intelmedia.ws
package monitoring

import argonaut.{DecodeResult => D, _}

import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream._

/** JSON encoders and decoders for types in the this library. */
object JSON {

  val R = Reportable
  val kindF = "kind"
  import EncodeJson.{
    DoubleEncodeJson, StringEncodeJson, Tuple2EncodeJson,
    jencode1, jencode2L, jencode6L, jencode7L
  }
  import DecodeJson.{jdecode6L}

  def encodeResult[A](a: A)(implicit A: EncodeJson[A]): Json = A(a)
  def encode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).nospaces
  def prettyEncode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).spaces2
  def sseDataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    "data: " + A(a).spaces2.replace("\n", "\ndata: ")

  def decodeUnion[A](kindF: String)(cases: (String, DecodeJson[A])*): DecodeJson[A] = {
    val byKind = cases.toMap
    DecodeJson { c =>
      (c --\ kindF).as[String].flatMap { kind =>
        byKind.get(kind).map(ctor => ctor(c))
              .getOrElse(D.fail("unknown kind: " + kind, c.history))
      }
    }
  }

  implicit def keyValue[A] = Tuple2EncodeJson(EncodeKey[A], EncodeReportable[A])

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
      case Stoplight => "Stoplight"
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
      case "Stoplight" => D.ok { Stoplight }
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
    jencode2L((k: Key[A]) => (k.label, k.id.toString))("label", "id")
  implicit def DecodeKey: DecodeJson[Key[Any]] = DecodeJson { c => for {
    lbl <- (c --\ "label").as[String]
    id <- (c --\ "id").as[String].flatMap { s =>
      try D.ok { java.util.UUID.fromString(s) }
      catch { case e: IllegalArgumentException => D.fail("invalid UUID", c.history) }
    }
  } yield new Key(lbl, id) }

  implicit def EncodeStats: EncodeJson[monitoring.Stats] =
    jencode7L((s: monitoring.Stats) =>
      (s.last, s.mean, s.count, s.variance, s.standardDeviation, s.skewness, s.kurtosis))(
       "last", "mean", "count", "variance", "standardDeviation", "skewness", "kurtosis")
  implicit def DecodeStats: DecodeJson[monitoring.Stats] =
    jdecode6L((last: Option[Double], mean: Double, count: Double, variance: Double,
               skewness: Double, kurtosis: Double) => {
      val m0 = count.toLong
      val m1 = mean
      // need to reverse the math to compute m2, m3, m4 from variance, skewness, kurtosis
      // see: https://github.com/twitter/algebird/blob/develop/algebird-core/src/main/scala/com/twitter/algebird/MomentsGroup.scala
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new monitoring.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    })("last", "mean", "count", "variance", "skewness", "kurtosis")

  implicit def EncodeReportable[A]: EncodeJson[Reportable[A]] = {
    import Reportable.{D => Dbl, _}
    EncodeJson {
      case Dbl(a) => encodeResult(a)
      case B(a) => encodeResult(a)
      case S(a) => encodeResult(a)
      case Stats(a) => encodeResult(a)
      case h => sys.error("unsupported reportable: " + h)
    }
  }

  implicit def DecodeReportable: DecodeJson[Reportable[Any]] = DecodeJson { c =>
    c.as[Double].map(R.D(_)) |||
    c.as[Boolean].map(R.B(_)) |||
    c.as[String].map(R.S(_)) |||
    c.as[monitoring.Stats].map(R.Stats(_))
  }

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given metrics to the `Writer`. This will block the calling
   * thread indefinitely.
   */
  def eventsToSSE(events: Process[Task, (Key[Any], Reportable[Any])],
                  sink: java.io.Writer): Unit =
    events.map(kv => s"event: reportable\n${sseDataEncode(kv)(keyValue[Any])}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given keys to the given `Writer`. This will block the calling
   * thread indefinitely.
   */
  def keysToSSE(events: Process[Task, Key[Any]], sink: java.io.Writer): Unit =
    events.map(k => s"event: key\n${sseDataEncode(k)}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  private def writeTo(sink: java.io.Writer): String => Unit =
    line => try {
      sink.write(line)
      sink.flush // this is a line-oriented protocol,
                 // so we flush after each line, otherwise
                 // consumer may get delayed messages
    }
    catch { case e: java.io.IOException =>
      // when client disconnects we'll get a broken pipe
      // IOException from the above `sink.write`. This
      // gets translated to normal termination
      throw Process.End
    }
}
