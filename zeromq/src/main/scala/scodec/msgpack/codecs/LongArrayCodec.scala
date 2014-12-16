package scodec.msgpack
package codecs

import scalaz.{\/, \/-, -\/}
import scalaz.std.vector._
import scalaz.syntax.traverse._
import scalaz.syntax.std.option._
import scodec.{Codec, Err}
import scodec.bits.BitVector

// FIXME: type conversion
private[codecs] class LongArrayCodec(size: Codec[Long]) extends Codec[Vector[MessagePack]] {

  def codec: Codec[MessagePack] = scodec.codecs.lazily { MessagePackCodec }

  override def encode(s: Vector[MessagePack]) = for {
    a <- s.traverseU { v => codec.encode(v) }.map { _.concatenate }
    n <- size.encode(s.length.toLong)
  } yield n ++ a

  override def decode(buffer: BitVector) =
    size.decode(buffer).flatMap { case (r, n) => {
      val builder = Vector.newBuilder[MessagePack]
      var remaining = r
      var error: Option[Err] = None
      for(_ <- 0 to n.toInt - 1) {
        codec.decode(remaining) match {
          case \/-((rest, value)) =>
            builder += value
            remaining = rest
          case -\/(err) =>
            error = Some(err)
            remaining = BitVector.empty
        }
      }
      error.toLeftDisjunction((BitVector.empty, builder.result))
    }}
}
