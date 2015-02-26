package funnel
package zeromq

class Version(val number: Int) extends AnyVal
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

  val v1 = new Version(1)
  val v2 = new Version(2)
  val unknown = new Version(0)
}
