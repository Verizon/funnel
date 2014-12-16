package scodec.msgpack

import scodec.Codec

trait WithJavaCodec[A]{
  def javaCodec: Codec[A]
  def scalaCodec: Codec[A]

  final def scalaEncoderJavaDecoder: Codec[A] =
    Codec(scalaCodec, javaCodec)

  final def javaEncoderScalaDecoder: Codec[A] =
    Codec(javaCodec, scalaCodec)
}

object WithJavaCodec {

  def apply[A](jcodec: Codec[A], scodec: Codec[A]): WithJavaCodec[A] =
    new WithJavaCodec[A] {
      override def scalaCodec = scodec
      override def javaCodec = jcodec
    }

  implicit val int: WithJavaCodec[Int] =
    apply(JavaCodec.int, scodec.msgpack.int)

  implicit val long: WithJavaCodec[Long] =
    apply(JavaCodec.long, scodec.msgpack.long)

  implicit val bool: WithJavaCodec[Boolean] =
    apply(JavaCodec.bool, scodec.msgpack.bool)

  implicit val float: WithJavaCodec[Float] =
    apply(JavaCodec.float, scodec.msgpack.float)

  implicit val double: WithJavaCodec[Double] =
    apply(JavaCodec.double, scodec.msgpack.double)

  implicit val str: WithJavaCodec[String] =
    apply(JavaCodec.str, scodec.msgpack.str)
}
