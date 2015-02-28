package funnel
package zeromq

abstract class Version(val number: Int)
object Versions {
  def fromInt(i: Int): Option[Version] =
    i match {
      case 1 => Option(v1)
      case 2 => Option(v2)
      case _ => None
    }

  def fromString(s: String): Option[Version] =
    try fromInt(s.toInt)
    catch {
      case e: NumberFormatException => None
    }

  val all: Seq[Version] = v1 :: v2 :: Nil

  case object `v1` extends Version(1)
  case object `v2` extends Version(2)
  case object `unknown` extends Version(0)
}
