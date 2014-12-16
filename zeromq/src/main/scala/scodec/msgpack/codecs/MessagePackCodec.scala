package scodec.msgpack
package codecs

import scalaz.NonEmptyList
import scodec.Codec
import scodec.bits.{BitVector, ByteVector}
import scodec.bits._
import scodec.codecs._

object MessagePackCodec extends Codec[MessagePack] {

  implicit val positiveFixInt: Codec[MPositiveFixInt] =
    (constant(bin"0") :: uint(7)).dropUnits.as[MPositiveFixInt]

  private def mmap(size: Codec[Int]): Codec[Map[MessagePack, MessagePack]] =
    lazily { new MapCodec(size) }

  implicit val fixMap: Codec[MFixMap] =
    (constant(bin"1000") :: mmap(uint(4))).dropUnits.as[MFixMap]

  private def array(size: Codec[Int]): Codec[Vector[MessagePack]] =
    lazily { new ArrayCodec(size) }

  implicit val fixArray: Codec[MFixArray] = (constant(bin"1001") :: array(uint(4))).dropUnits.as[MFixArray]

  implicit val fixStr: Codec[MFixString] =
    (constant(bin"101") :: variableSizeBytes(uint(5), utf8)).dropUnits.as[MFixString]

  implicit val nil: Codec[MNil.type] = constant(hex"c0") ~> provide(MNil)

  implicit val mFalse: Codec[MFalse.type] = constant(hex"c2") ~> provide(MFalse)
  implicit val mTrue: Codec[MTrue.type] = constant(hex"c3") ~> provide(MTrue)

  implicit val bin8: Codec[MBinary8] =
    (constant(hex"c4") :: variableSizeBytes(uint8, bytes)).dropUnits.as[MBinary8]
  implicit val bin16: Codec[MBinary16] =
    (constant(hex"d5") :: variableSizeBytes(uint16, bytes)).dropUnits.as[MBinary16]
  implicit val bin32: Codec[MBinary32] =
    (constant(hex"c6") :: variableSizeBytesL(uint32, bytes)).dropUnits.as[MBinary32]

  private def extended(size: Codec[Int]) = size.flatPrepend { n => bytes(1) :: bytes(n) }

  implicit val ext8: Codec[MExtended8] =
    (constant(hex"c7") :: extended(uint8)).dropUnits.as[MExtended8]
  implicit val ext16: Codec[MExtended16] =
    (constant(hex"c8") :: extended(uint16)).dropUnits.as[MExtended16]

  // FIXME: type conversion
  implicit val ext32: Codec[MExtended32] =
    (constant(hex"c9") :: (uint32.flatPrepend { n => bytes(1) :: bytes(n.toInt) })).dropUnits.as[MExtended32]

  implicit val float32: Codec[MFloat32] =
    (constant(hex"ca") :: float).dropUnits.as[MFloat32]
  implicit val float64: Codec[MFloat64] =
    (constant(hex"cb") :: double).dropUnits.as[MFloat64]

  implicit val muint8: Codec[MUInt8] =
    (constant(hex"cc") :: uint8).dropUnits.as[MUInt8]
  implicit val muint16: Codec[MUInt16] =
    (constant(hex"cd") :: uint16).dropUnits.as[MUInt16]
  implicit val muint32: Codec[MUInt32] =
    (constant(hex"ce") :: uint32).dropUnits.as[MUInt32]
  implicit val muint64: Codec[MUInt64] =
    (constant(hex"cf") :: long(64)).dropUnits.as[MUInt64]

  implicit val mint8: Codec[MInt8] =
    (constant(hex"d0") :: int8).dropUnits.as[MInt8]
  implicit val mint16: Codec[MInt16] =
    (constant(hex"d1") :: int16).dropUnits.as[MInt16]
  implicit val mint32: Codec[MInt32] =
    (constant(hex"d2") :: int32).dropUnits.as[MInt32]
  implicit val mint64: Codec[MInt64] =
    (constant(hex"d3") :: int64).dropUnits.as[MInt64]

  implicit val fixExt1: Codec[MFixExtended1] =
    (constant(hex"d4") :: bytes(1) :: bytes(1)).dropUnits.as[MFixExtended1]
  implicit val fixExt2: Codec[MFixExtended2] =
    (constant(hex"d5") :: bytes(1) :: bytes(2)).dropUnits.as[MFixExtended2]
  implicit val fixExt4: Codec[MFixExtended4] =
    (constant(hex"d6") :: bytes(1) :: bytes(4)).dropUnits.as[MFixExtended4]
  implicit val fixExt8: Codec[MFixExtended8] =
    (constant(hex"d7") :: bytes(1) :: bytes(8)).dropUnits.as[MFixExtended8]
  implicit val fixExt16: Codec[MFixExtended16] =
    (constant(hex"d8") :: bytes(1) :: bytes(16)).dropUnits.as[MFixExtended16]

  private def variableSizeBytesL[A](size: Codec[Long], value: Codec[A]): Codec[A] =
    new VariableLongSizeBytesCodec(size, value)

  implicit val str8: Codec[MString8] =
    (constant(hex"d9") :: variableSizeBytes(uint8, utf8)).dropUnits.as[MString8]
  implicit val str16: Codec[MString16] =
    (constant(hex"da") :: variableSizeBytes(uint16, utf8)).dropUnits.as[MString16]
  implicit val str32: Codec[MString32] =
    (constant(hex"db") :: variableSizeBytesL(uint32, utf8)).dropUnits.as[MString32]

  implicit val array16: Codec[MArray16] =
    (constant(hex"dc") :: array(uint16)).dropUnits.as[MArray16]

  private def longArray(size: Codec[Long]): Codec[Vector[MessagePack]] =
    lazily { new LongArrayCodec(size) }

  implicit val array32: Codec[MArray32] =
    (constant(hex"dd") :: longArray(uint32)).dropUnits.as[MArray32]

  implicit val map16: Codec[MMap16] =
    (constant(hex"de") :: mmap(uint16)).dropUnits.as[MMap16]

  private def longMap(size: Codec[Long]): Codec[Map[MessagePack, MessagePack]] =
    lazily { new LongMapCodec(size) }

  implicit val map32: Codec[MMap32] =
    (constant(hex"df") :: longMap(uint32)).dropUnits.as[MMap32]

  implicit val negativeFixInt: Codec[MNegativeFixInt] =
    (constant(bin"111") :: uint(5).xmap(_ - 0x20, (a: Int) => a + 0x20)).dropUnits.as[MNegativeFixInt]

  private val codec: Codec[MessagePack] =
    scodec.codecs.lazily { Codec.coproduct[MessagePack].choice }

  def encode(m: MessagePack) = codec.encode(m)
  def decode(m: BitVector) = codec.decode(m)
}
