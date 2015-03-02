package funnel
package zeromq

case class Window(window: String) extends AnyVal {
  override def toString = window
}

object Windows {
  def fromString(str: String): Option[Window] = str match {
    case "now" => Some(now)
    case "previous" => Some(previous)
    case "sliding" => Some(sliding)
    case _ => None
  }

  val now = Window("now")
  val previous = Window("previous")
  val sliding = Window("sliding")
  val unknown = Window("unknown")
}
