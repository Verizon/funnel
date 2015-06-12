package funnel
package chemist
package aws

sealed trait MirrorMode {
  def protocol: String
  def port: Int
}
object MirrorMode {
  def fromString(in: String): Option[MirrorMode] =
    in.toLowerCase.trim match {
      case "zeromq" => Some(ZeroMQ)
      case "http"   => Some(Http)
      case _        => None
    }

  case object ZeroMQ extends MirrorMode {
    val protocol = "tcp"
    val port = 7390
  }
  case object Http extends MirrorMode {
    val protocol = "http"
    val port = 5775
  }
}
