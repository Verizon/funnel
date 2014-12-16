package scodec.msgpack
package codecs

import scalaz.{\/, \/-, -\/}
import scalaz.syntax.either._
import scalaz.syntax.std.option._
import scodec.{Codec, Err}
import scodec.bits.BitVector

private[codecs] class LongMapCodec(size: Codec[Long]) extends Codec[Map[MessagePack, MessagePack]] {

  def pair: Codec[(MessagePack, MessagePack)] =
    scodec.codecs.lazily { MessagePackCodec ~ MessagePackCodec }

  override def encode(m: Map[MessagePack, MessagePack]) = for {
    a <- m.foldLeft(BitVector.empty.right[Err])((acc, p) => acc match {
      case -\/(_) => pair.encode(p)
      case \/-(v) => pair.encode(p).map(v ++ _)
      })
    n <- size.encode(m.size.toLong)
  } yield n ++ a

  override def decode(buffer: BitVector) =
    size.decode(buffer).flatMap { case (r, n) => {
      val builder = Map.newBuilder[MessagePack, MessagePack]
      var remaining = r
      var error: Option[Err] = None
      for(_ <- 0 to n.toInt - 1) {
        pair.decode(remaining) match {
          case \/-((rest, (k, v))) =>
            builder += k -> v
            remaining = rest
          case -\/(err) =>
            error = Some(err)
            remaining = BitVector.empty
        }
      }
      error.toLeftDisjunction((BitVector.empty, builder.result))
    }}
}
