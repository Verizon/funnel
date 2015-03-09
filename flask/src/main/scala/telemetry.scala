package funnel
package flask

import zeromq._
import scodec._
import shapeless.Sized
import java.util.concurrent.TimeUnit
import TimeUnit._

sealed trait Telemetry

final case class Error(names: Names) extends Telemetry {
  override def toString = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
}

final case class NewKey(key: Key[_]) extends Telemetry
object Telemetry {
  implicit val utf8: Codec[String] = codecs.variableSizeBytes(codecs.int32, codecs.utf8)
  val uint8 = codecs.uint8

  implicit def tuple2[A:Codec,B: Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def indexedSeq[A:Codec]: Codec[IndexedSeq[A]] =
    codecs.variableSizeBytes(codecs.int32, codecs.vector(Codec[A]).xmap(a => a, _.toVector))

  implicit def map[K:Codec,V:Codec]: Codec[Map[K,V]] =
    indexedSeq[(K,V)].xmap[Map[K,V]](
      _.toMap,
      _.toIndexedSeq
    )

  implicit val errorCodec = Codec.derive[Names].xmap[Error](Error(_), _.names)
  implicit val telemetryCodec: Codec[Telemetry] = (errorCodec :+: Codec.derive[NewKey]).discriminatedBy(uint8).using(Sized(1,2)).as[Telemetry]

  implicit val reportableCodec: Codec[Reportable[Any]] = (codecs.provide(Reportable.B) :+: codecs.provide(Reportable.D) :+: codecs.provide(Reportable.S) :+: codecs.provide(Reportable.Stats)).discriminatedByIndex(uint8).as[Reportable[Any]]

  implicit val baseCodec: Codec[Units.Base] = {
    import Units.Base._
      (codecs.provide(Zero) :+: codecs.provide(Kilo) :+: codecs.provide(Mega) :+: codecs.provide(Giga)).discriminatedByIndex(uint8).as[Units.Base]
  }

  val tuToInt: TimeUnit => Int = _ match {
      case DAYS => 0
      case HOURS => 1
      case MICROSECONDS => 2
      case MILLISECONDS => 3
      case MINUTES => 4
      case NANOSECONDS => 5
      case SECONDS => 6
  }
  val intToTU: Int => TimeUnit = _ match {
      case 0 => DAYS
      case 1 => HOURS
      case 2 => MICROSECONDS
      case 3 => MILLISECONDS
      case 4 => MINUTES
      case 5 => NANOSECONDS
      case 6 => SECONDS
  }

  implicit val timeUnitCodec: Codec[TimeUnit] = codecs.uint8.xmap(intToTU, tuToInt)

  implicit val unitsCodec: Codec[Units[Any]] = {
    import Units._
    (Codec.derive[Duration] :+:
       Codec.derive[Bytes] :+:
       codecs.provide(Count) :+:
       codecs.provide(Ratio) :+:
       codecs.provide(TrafficLight) :+:
       codecs.provide(Healthy) :+:
       codecs.provide(Load) :+:
       codecs.provide(None)).discriminatedByIndex(uint8).as[Units[Any]]
  }

  implicit val keyCodec: Codec[Key[Any]] = (utf8 :: reportableCodec :: unitsCodec :: utf8 :: map[String,String]).as[Key[Any]]

  implicit val transportedTelemetry = Transportable[Telemetry] { t =>
    t match {
      case e @ Error(_) => Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("error")), telemetryCodec.encodeValid(e).toByteArray)
      case k @ NewKey(_) => Transported(Schemes.telemetry, Versions.v1, None, Some(Topic("key")), telemetryCodec.encodeValid(k).toByteArray)
    }
  }
}
