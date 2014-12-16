package scodec
package msgpack
package examples

import org.scalatest.prop.Checkers

class JsonStyleExample extends TestSuite {

  sealed abstract class Setting
  case class IsCompact(v: Boolean) extends Setting
  case class SchemaSize(v: Int) extends Setting

  implicit val stringS = Serialize.string
  implicit val settingSerializer = new Serialize[Setting] {
    def pack(v: Setting) = v match {
      case IsCompact(v) => Serialize.bool.pack(v)
      case SchemaSize(v) => Serialize.int.pack(v)
    }
    def unpack(v: MessagePack) =
      Serialize.bool.unpack(v).map(IsCompact)
        .orElse(Serialize.int.unpack(v).map(SchemaSize))
  }

  "json style data" should "encode and decode" in {
    val input = Map("compact" -> IsCompact(true), "schema" -> SchemaSize(0))
    roundtrip(msgpack.map[String, Setting], input)
  }
}
