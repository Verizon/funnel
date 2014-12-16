package scodec.msgpack
package codecs

import scodec.bits.ByteVector

class MessagePackCodecSpec extends TestSuite {

  "nil" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MNil)
  }

  "bool" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MTrue)
    roundtrip(MessagePackCodec, MFalse)
  }

  "positive fix int" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MPositiveFixInt(0))
    roundtrip(MessagePackCodec, MPositiveFixInt(127))
  }

  "uint8" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MUInt8(0))
    roundtrip(MessagePackCodec, MUInt8(255))
  }

  "uint16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MUInt16(0))
    roundtrip(MessagePackCodec, MUInt16(65535))
  }

  "uint32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MUInt32(0))
    roundtrip(MessagePackCodec, MUInt32(4294967295L))
  }

  "negative fix int" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MNegativeFixInt(-32))
    roundtrip(MessagePackCodec, MNegativeFixInt(-1))
  }

  "int8" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MInt8(-128))
    roundtrip(MessagePackCodec, MInt8(127))
  }

  "int16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MInt16(-32768))
    roundtrip(MessagePackCodec, MInt16(32767))
  }

  "int32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MInt32(Int.MinValue))
    roundtrip(MessagePackCodec, MInt32(Int.MaxValue))
  }

  "int64" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MInt64(Long.MinValue))
    roundtrip(MessagePackCodec, MInt64(Long.MaxValue))
  }

  "float32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MFloat32(Float.MinValue))
    roundtrip(MessagePackCodec, MFloat32(Float.MaxValue))
  }

  "float64" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MFloat64(Double.MinValue))
    roundtrip(MessagePackCodec, MFloat64(Double.MaxValue))
  }

  "fix str" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MFixString(""))
    roundtrip(MessagePackCodec, MFixString("a" * 10))
  }

  "str8" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MString8(""))
    roundtrip(MessagePackCodec, MString8("a" * 255))
  }

  "str16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MString16(""))
    roundtrip(MessagePackCodec, MString16("a" * 65535))
  }

  "str32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MString32(""))
    roundtrip(MessagePackCodec, MString32("a" * Long.MaxValue.toInt))
  }

  "bin8" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MBinary8(ByteVector.empty))
    roundtrip(MessagePackCodec, MBinary8(ByteVector(0xa0)))
  }

  "bin16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MBinary16(ByteVector.empty))
    roundtrip(MessagePackCodec, MBinary16(ByteVector(0xff)))
  }

  "bin32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MBinary32(ByteVector.empty))
    roundtrip(MessagePackCodec, MBinary32(ByteVector(0x11)))
  }

  "fix array" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MFixArray(Vector.empty[MessagePack]))
    roundtrip(MessagePackCodec, MFixArray(Vector(MInt8(127))))
  }

  "array16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MArray16(Vector.empty[MessagePack]))
    roundtrip(MessagePackCodec, MArray16(Vector(MInt8(127))))
  }

  "array32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MArray32(Vector.empty[MessagePack]))
    roundtrip(MessagePackCodec, MArray32(Vector(MInt8(127))))
  }

  "fix map" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MFixMap(Map.empty))
    roundtrip(MessagePackCodec, MFixMap(Map(MFixString("a") -> MInt8(1))))
  }

  "map16" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MMap16(Map.empty))
    roundtrip(MessagePackCodec, MMap16(Map(MFixString("a") -> MInt8(1))))
  }

  "map32" should "be able to encode and decode" in {
    roundtrip(MessagePackCodec, MMap32(Map.empty))
    roundtrip(MessagePackCodec, MMap32(Map(MFixString("a") -> MInt8(1))))
  }
}
