package scodec.msgpack
package codecs

import scodec.Codec
import scodec.codecs._
import scodec.bits.BitVector

// https://github.com/scodec/scodec/blob/79623b7339de079fa62b70105dd1614df33703b7/src/main/scala/scodec/codecs/VariableSizeCodec.scala
// FIXME: type conversion
private[codecs] class VariableLongSizeBytesCodec[A](size: Codec[Long], value: Codec[A]) extends Codec[A] {

  override def encode(a: A) = for {
    encodeA <- value.encode(a)
    encodeSize <- size.encode(encodeA.size)
  } yield encodeSize ++ encodeA

  override def decode(buffer: BitVector) =
    size.flatZip(sz => fixedSizeBits(sz.toInt, value)).decode(buffer).map { case (rest, (sz, v)) => (rest, v) }
}
