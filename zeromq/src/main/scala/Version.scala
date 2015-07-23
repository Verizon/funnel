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

  val all: Seq[Version] =
    v1 :: v2 :: Nil

  /**
   * Of all protocol versions, what are the current versions we
   * support.
   *
   * TIM: consider somehow taking this as a configuration param
   * at a later date so that its not hard coded.
   */
  val supported: Seq[Version] =
    v1 :: Nil

  // JSON over ZMTP
  lazy val v1 = new Version(1)

  // Binary scodec over ZMTP
  lazy val v2 = new Version(2)

  // Something went really wrong...
  lazy val unknown = new Version(0)
}
