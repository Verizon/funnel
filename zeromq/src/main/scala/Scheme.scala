package funnel
package zeromq

case class Scheme(scheme: String) extends AnyVal {
  override def toString = scheme
}

object Schemes {
  def fromString(str: String): Scheme = {
    str match {
      case "fsm"   => fsm
      case "telem" => telemetry
      case _       => unknown
    }
  }

  // used for reporting datapoints
  val fsm = Scheme("fsm")

  // used for the admin channel between flask and chemist
  val telemetry = Scheme("telem")

  // something really wrong happened.
  val unknown = Scheme("unknown")
}
