package scodec.msgpack

import scalaz.std.option._
import scalaz.syntax.std.option._
import scalaz.std.vector._
import scalaz.syntax.std.map._
import scalaz.syntax.traverse._
import scodec.Codec
import scodec.bits.ByteVector

abstract class Serialize[A] {

  def pack(v: A): MessagePack
  def unpack(v: MessagePack): Option[A]
}

object Serialize {

  val bool: Serialize[Boolean] = new Serialize[Boolean] {

    def pack(v: Boolean): MessagePack = if (v) MTrue else MFalse

    def unpack(v: MessagePack): Option[Boolean] = v match {
      case m : MBool => m.value.some
      case _ => None
    }
  }

  val int: Serialize[Int] =new Serialize[Int] {

    // This implementation refer to msgpack-java (Apache License version 2.0).
    // https://github.com/msgpack/msgpack-java/blob/bd9b4f20597111775120546de41dd3f9d01b9616/msgpack-core/src/main/java/org/msgpack/core/MessagePacker.java#L253
    // Change log: fix indent and return values.
    def pack(v: Int): MessagePack =
      if (v < -(1 << 5)) {
        if (v < -(1 << 15)) MInt32(v)
        else if (v < -(1 << 7)) MInt16(v)
        else MInt8(v)
      }
      else if (v < (1 << 7)) {
        if (v >= 0) MPositiveFixInt(v)
        else MNegativeFixInt(v)
      }
      else {
        if (v < (1 << 8)) MUInt8(v)
        else if (v < (1 << 16)) MUInt16(v)
        else MUInt32(v)
      }

    def unpack(v: MessagePack): Option[Int] = v match {
      case MPositiveFixInt(n) => n.some
      case MNegativeFixInt(n) => n.some
      case MUInt8(n) => n.some
      case MUInt16(n) => n.some
      case MUInt32(n) => n.toInt.some
      case MInt8(n) => n.some
      case MInt16(n) => n.some
      case MInt32(n) => n.some
      case _ => None
    }
  }

  val long: Serialize[Long] =new Serialize[Long] {

    // This implementation refer to msgpack-java (Apache License version 2.0).
    // https://github.com/msgpack/msgpack-java/blob/bd9b4f20597111775120546de41dd3f9d01b9616/msgpack-core/src/main/java/org/msgpack/core/MessagePacker.java#L277
    // Change log: fix indent and return values.
    def pack(v: Long): MessagePack =
      if (v < -(1L << 5)) {
        if (v < -(1L << 15)) {
          if (v < -(1L << 31)) MInt64(v)
          else  MInt32(v.toInt)
        }
        else {
          if (v < -(1 << 7)) MInt16(v.toInt)
          else MInt8(v.toInt)
        }
      }
      else if (v < (1 << 7)) {
        if (v >= 0) MPositiveFixInt(v.toInt)
        else MNegativeFixInt(v.toInt)
      }
      else {
        if (v < (1L << 16)) {
          if (v < (1 << 8)) MUInt8(v.toInt)
          else MUInt16(v.toInt)
        }
        else {
          if (v < (1L << 32)) MUInt32(v.toInt)
          else MUInt64(v)
        }
      }

    def unpack(v: MessagePack): Option[Long] = v match {
      case MPositiveFixInt(n) => n.toLong.some
      case MNegativeFixInt(n) => n.toLong.some
      case MUInt8(n) => n.toLong.some
      case MUInt16(n) => n.toLong.some
      case MUInt32(n) => n.some
      case MUInt64(n) => n.some
      case MInt8(n) => n.toLong.some
      case MInt16(n) => n.toLong.some
      case MInt32(n) => n.toLong.some
      case MInt64(n) => n.some
      case _ => None
    }
  }

  val float: Serialize[Float] =new Serialize[Float] {

    def pack(v: Float): MessagePack = MFloat32(v)

    def unpack(v: MessagePack): Option[Float] = v match {
      case MFloat32(n) => n.some
      case _ => None
    }
  }

  val double: Serialize[Double] =new Serialize[Double] {

    def pack(v: Double): MessagePack = MFloat64(v)

    def unpack(v: MessagePack): Option[Double] = v match {
      case MFloat64(n) => n.some
      case _ => None
    }
  }

  val string: Serialize[String] =new Serialize[String] {
    def pack(v: String): MessagePack = {
      val len = v.getBytes("UTF-8").length
      if(len <= 31) MFixString(v)
      else if(len <= 255) MString8(v)
      else if(len <= 65535) MString16(v)
      else MString32(v)
    }

    def unpack(v: MessagePack): Option[String] = v match {
      case MFixString(n) => n.some
      case MString8(n) => n.some
      case MString16(n) => n.some
      case MString32(n) => n.some
      case _ => None
    }
  }

  val binary: Serialize[ByteVector] =new Serialize[ByteVector] {
    def pack(v: ByteVector): MessagePack = {
      val len = v.size
      if(len <= 255) MBinary8(v)
      else if(len <= 65535) MBinary16(v)
      else MBinary32(v)
    }

    def unpack(v: MessagePack): Option[ByteVector] = v match {
      case MBinary8(n) => n.some
      case MBinary16(n) => n.some
      case MBinary32(n) => n.some
      case _ => None
    }
  }

  def array[A](implicit S: Serialize[A]): Serialize[Vector[A]] =new Serialize[Vector[A]] {
    def pack(v: Vector[A]): MessagePack = {
      val len = v.size
      val vm = v.map(S.pack)
      if(len <= 15) MFixArray(vm)
      else if(len <= 65535) MArray16(vm)
      else MArray32(vm)
    }

    def unpack(v: MessagePack): Option[Vector[A]] = v match {
      case MFixArray(n) => n.map(S.unpack).sequence
      case MArray16(n) => n.map(S.unpack).sequence
      case MArray32(n) => n.map(S.unpack).sequence
      case _ => None
    }
  }

  def map[A, B](implicit S: Serialize[A], T: Serialize[B]): Serialize[Map[A, B]] =new Serialize[Map[A, B]] {
    def pack(v: Map[A, B]): MessagePack = {
      val len = v.size
      val vm = v.mapValues(T.pack _).mapKeys(S.pack _)
      if(len <= 15) MFixMap(vm)
      else if(len <= 65535) MMap16(vm)
      else MMap32(vm)
    }

    def unpack(v: MessagePack): Option[Map[A, B]] = v match {
      case MFixMap(n) => n.toVector.map { case (k, v) => S.unpack(k).flatMap(kk => T.unpack(v).map((kk, _))) }.sequence.map(_.toMap)
      case MMap16(n) => n.toVector.map { case (k, v) => S.unpack(k).flatMap(kk => T.unpack(v).map((kk, _))) }.sequence.map(_.toMap)
      case MMap32(n) => n.toVector.map { case (k, v) => S.unpack(k).flatMap(kk => T.unpack(v).map((kk, _))) }.sequence.map(_.toMap)
      case _ => None
    }
  }

  def extended[A](code: ByteVector)(implicit S: Codec[A]): Serialize[A] =new Serialize[A] {
    def pack(v: A): MessagePack = {
      // TODO: use S.encode
      val encoded = S.encodeValid(v).bytes
      val len = encoded.size
      if(len <= 1) MFixExtended1(code, encoded)
      if(len <= 2) MFixExtended2(code, encoded)
      if(len <= 4) MFixExtended4(code, encoded)
      if(len <= 8) MFixExtended8(code, encoded)
      if(len <= 16) MFixExtended16(code, encoded)
      if(len <= 256) MExtended8(len, code, encoded)
      else if(len <= 65536) MExtended16(len, code, encoded)
      else MExtended32(len.toLong, code, encoded)
    }

    def unpack(v: MessagePack): Option[A] = v match {
      case MFixExtended1(_, value) => S.decodeValue(value.bits).toOption
      case MFixExtended2(_, value) => S.decodeValue(value.bits).toOption
      case MFixExtended4(_, value) => S.decodeValue(value.bits).toOption
      case MFixExtended8(_, value) => S.decodeValue(value.bits).toOption
      case MFixExtended16(_, value) => S.decodeValue(value.bits).toOption
      case MExtended8(_, _, value) => S.decodeValue(value.bits).toOption
      case MExtended16(_, _, value) => S.decodeValue(value.bits).toOption
      case MExtended32(_, _, value) => S.decodeValue(value.bits).toOption
      case _ => None
    }
  }
}
