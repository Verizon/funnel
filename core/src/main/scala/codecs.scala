package funnel

import scodec._
import scodec.bits._
import shapeless.Sized
import scalaz.\/

import java.util.concurrent.TimeUnit
import TimeUnit._

trait Codecs {
  implicit val utf8: Codec[String] = codecs.variableSizeBytes(codecs.int32, codecs.utf8)

  implicit val uint8 = codecs.uint8

  implicit def tuple2[A:Codec,B: Codec]: Codec[(A,B)] =
    Codec[A] ~ Codec[B]

  implicit def indexedSeq[A:Codec]: Codec[IndexedSeq[A]] =
    codecs.variableSizeBytes(codecs.int32, codecs.vector(Codec[A]).xmap(a => a, _.toVector))

  implicit def map[K:Codec,V:Codec]: Codec[Map[K,V]] =
    indexedSeq[(K,V)].xmap[Map[K,V]](
      _.toMap,
      _.toIndexedSeq
    )
}

trait KeyCodecs extends Codecs { self =>

  val keyEncode: Encoder[Key[Any]] = new Encoder[Key[Any]] {
    def encode(k: Key[Any]): Err \/ BitVector = {
      for {
        b1 <- k.typeOf match {
          case Reportable.B => uint8.encode(1)
          case Reportable.D => uint8.encode(2)
          case Reportable.S => uint8.encode(3)
          case Reportable.Stats => uint8.encode(4)
        }

        b2 <- utf8.encode(k.name)
        b3 <- unitsCodec.encode(k.units)
        b4 <- utf8.encode(k.description)
        b5 <- self.map[String,String].encode(k.attributes)
      } yield {
        b1 ++ b2 ++ b3 ++ b4 ++ b5
      }
    }
  }

  val keyDecode: Decoder[Key[Any]] = new Decoder[Key[Any]] {
    def decode(bits: BitVector): Err \/ (BitVector, Key[Any]) = {
      uint8.decode(bits) flatMap {
        case (bits,disc) =>
          val reportable = disc match {
            case 1 => Reportable.B
            case 2 => Reportable.D
            case 3 => Reportable.S
            case _ => Reportable.Stats
          }
          _decodeKey(reportable).decode(bits)
      }
    }
  }

  def dataEncode[A](k: Key[A], a: A) =
    k.typeOf match {
      case Reportable.B => codecs.bool.encode(a.asInstanceOf[Boolean])
      case Reportable.D => codecs.double.encode(a.asInstanceOf[Double])
      case Reportable.S => utf8.encode(a.asInstanceOf[String])
      case Reportable.Stats => statsCodec.encode(a.asInstanceOf[Stats])
    }

  val datapointEncode: Encoder[Datapoint[Any]] = new Encoder[Datapoint[Any]] {
    def encode(d: Datapoint[Any]): Err \/ BitVector = {
      for {
        bk <- keyEncode.encode(d.key)
        bv <- dataEncode(d.key, d.value)
      } yield bk ++ bv
    }
  }

  val datapointDecode: Decoder[Datapoint[Any]] = new Decoder[Datapoint[Any]] {
    def decode(bits: BitVector): Err \/ (BitVector, Datapoint[Any]) = {
      def go(R: Reportable[Any]) = for {
        key_ <- _decodeKey(R).decode(bits)
        (bits2, key) = key_
        v_ <- _decodeData(bits2, R)
        (bits3,v) = v_
      } yield bits3 -> Datapoint(key, v)
      uint8.decode(bits) flatMap {
        case (bits,disc) => go(
          disc match {
            case 1 => Reportable.B
            case 2 => Reportable.D
            case 3 => Reportable.S
            case _ => Reportable.Stats
          })
      }
    }
  }

  private def _decodeKey(reportable: Reportable[Any]): Decoder[Key[Any]] = new Decoder[Key[Any]] {
    def decode(bits: BitVector): Err \/ (BitVector, Key[Any]) = {
      for {
        name_ <- utf8.decode(bits)
        (bits1, name) = name_

        units_ <- unitsCodec.decode(bits1)
        (bits2, units) = units_

        desc_ <- utf8.decode(bits2)
        (bits3, desc) = desc_

        attributes_ <- self.map[String,String].decode(bits3)
        (bits4, attributes) = attributes_

        initialValue_ <- _decodeData(bits4, reportable)
        (bits5, initialValue) = initialValue_

      } yield bits5 -> Key(name, reportable, units, desc, attributes, initialValue)
    }
  }

  private def _decodeData(bits: BitVector, reportable: Reportable[Any]) =
    reportable match {
      case Reportable.B => codecs.bool.decode(bits)
      case Reportable.D => codecs.double.decode(bits)
      case Reportable.S => utf8.decode(bits)
      case Reportable.Stats => statsCodec.decode(bits)
    }

  implicit val statsCodec: Codec[Stats] = (
    codecs.optional(codecs.bool, codecs.double) ~
      codecs.double ~
      codecs.uint32L ~
      codecs.double ~
      codecs.double ~
      codecs.double ~
      codecs.double).xmap(
    {case ((((((last,mean),count),variance),standardDeviation),skewness),kurtosis) =>
      val m0 = count.toLong
      val m1 = mean
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new funnel.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    },
    { stats => ((((((stats.last, stats.mean),
                    stats.count),
                   stats.variance),
                  stats.standardDeviation),
                 stats.skewness),
                stats.kurtosis)}
  )

  lazy implicit val baseCodec: Codec[Units.Base] = {
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

  implicit def unitsCodec: Codec[Units] = {
    import Units._
    (Codec.derive[Duration] :+:
       Codec.derive[Bytes] :+:
       codecs.provide(Count) :+:
       codecs.provide(Ratio) :+:
       codecs.provide(TrafficLight) :+:
       codecs.provide(Healthy) :+:
       codecs.provide(Load) :+:
       codecs.provide(None)).discriminatedByIndex(uint8).as[Units]
  }
}
