package funnel
package zeromq

case class Scheme(scheme: String) extends AnyVal {
  override def toString = scheme
}

object Schemes {
  def fromString(str: String): Scheme = {
    str match {
      case "fsm" => fsm
      case "telem" => telemetry
      case _ => unknown
    }
  }

  val fsm = Scheme("fsm")
  val telemetry = Scheme("telem")
  val unknown = Scheme("unkown")
}
