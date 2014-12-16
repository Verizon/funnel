package scodec.msgpack

import scodec._
import scodec.bits.BitVector
import org.scalatest.FlatSpec
import org.scalatest.DiagrammedAssertions
import scalaz.{\/-, -\/}

abstract class TestSuite extends FlatSpec with DiagrammedAssertions {

  def roundtrip[A](codec: Codec[A], a: A) = {
    codec.encode(a) match {
      case -\/(error) =>
        fail(error.toString())
      case \/-(encoded) =>
        codec.decode(encoded) match {
          case -\/(error) =>
            fail(error.toString())
          case \/-((remainder, decoded)) =>
            assert(remainder === BitVector.empty)
            assert(decoded === a)
            decoded === a
        }
    }
  }

  def roundtripWithJava[A](a: A)(implicit C: WithJavaCodec[A]) = {
    roundtrip(C.javaEncoderScalaDecoder, a)
    roundtrip(C.scalaEncoderJavaDecoder, a)
  }
}

