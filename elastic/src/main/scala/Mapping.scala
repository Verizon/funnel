package oncue.svc.funnel
package elastic

import argonaut._
import Argonaut._
import scalaz._
import Scalaz._

// A (very) partial specification of the ElasticSearch mapping API

case class Mappings(types: List[Mapping])
case class Mapping(name: String, properties: Properties)
case class Properties(fields: List[Field])
case class Field(name: String, tpe: FieldType, meta: Json = jEmptyObject)


sealed abstract class FieldType(val name: String)
case object StringField extends FieldType("string")
case object FloatField extends FieldType("float")
case object DoubleField extends FieldType("double")
case object ByteField extends FieldType("byte")
case object ShortField extends FieldType("short")
case object IntField extends FieldType("integer")
case object LongField extends FieldType("long")
case object TokenCountField extends FieldType("token_count")
case object BoolField extends FieldType("boolean")
case object BinaryField extends FieldType("binary")
case class DateField(format: Option[String] = None) extends FieldType("date")
case class ObjectField(properties: Properties) extends FieldType("object")
case class NestedField(properties: Properties,
                       includeInParent: Option[Boolean] = None) extends FieldType("nested")

object Mapping {
  import DecodeResult._

  implicit def PropsCodec: CodecJson[Properties] = CodecJson(
    ps => jObjectAssocList(ps.fields.map { f =>
      f.name -> jObjectAssocList(
        List("type" -> jString(f.tpe.name), "_meta" -> f.meta) ++ (f.tpe match {
          case DateField(Some(fmt)) => List("format" -> jString(fmt))
          case ObjectField(props) => List("properties" -> props.asJson(PropsCodec))
          case NestedField(props, inc) => List("properties" -> props.asJson(PropsCodec)) ++
            inc.map(x => List("include_in_parent" -> x.asJson)).getOrElse(Nil)
          case _ => Nil
      }))
    }),
    c => c.fields.traverse(_.traverse { p =>
      val prop = c --\ p
      for {
        tpe <- (prop --\ "type").as[String]
        r <- tpe match {
          case "string" => ok(StringField)
          case "float" => ok(FloatField)
          case "double" => ok(DoubleField)
          case "byte" => ok(ByteField)
          case "short" => ok(ShortField)
          case "integer" => ok(IntField)
          case "long" => ok(LongField)
          case "token_count" => ok(TokenCountField)
          case "boolean" => ok(BoolField)
          case "binary" => ok(BinaryField)
          case "date" =>
            (prop --\ "format").as[Option[String]].map(DateField(_))
          case "object" =>
            (prop --\ "properties").as[Properties](PropsCodec).map(ObjectField(_))
          case "nested" => for {
            fields <- (prop --\ "properties").as[Properties]
            incl   <- (prop --\ "include_in_parent").as[Option[Boolean]]
          } yield NestedField(fields, incl)
        }
        meta <- (prop --\ "_meta").as[Json] ||| ok(jEmptyObject)
      } yield Field(p, r, meta)
    }).map(x => Properties(x.toList.flatten))
  )

  implicit def MappingCodec: CodecJson[Mappings] = CodecJson(
    ms => jObjectAssocList(ms.types.map { m =>
      m.name -> Json("properties" -> m.properties.asJson)
    }),
    c => c.fields.traverse(_.traverse { m =>
      (c --\ m --\ "properties").as[Properties].map(Mapping(m, _))
    }).map(x => Mappings(x.toList.flatten))
  )
}
