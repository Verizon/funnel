package funnel
package zeromq

class Version(val number: Int) extends AnyVal {
  override def toString = number.toString
}
object Versions {
  def fromInt(i: Int): Version =
    i match {
      case 1 => v1
      case 2 => v2
      case _ => unknown
    }

  def fromString(s: String): Version =
    try fromInt(s.toInt)
    catch {
      case e: NumberFormatException => unknown
    }

  val all: Seq[Version] = v1 :: v2 :: Nil

  val v1 = new Version(1)
  val v2 = new Version(2)
  val unknown = new Version(0)
}
