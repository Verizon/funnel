package funnel
package chemist

import java.net.URI

sealed abstract class PlatformEvent {
  val time = new java.util.Date
}

object PlatformEvent {
  final case class NewTarget(target: Target) extends PlatformEvent
  final case class NewFlask(flask: Flask) extends PlatformEvent
  final case class TerminatedTarget(u: URI) extends PlatformEvent
  final case class TerminatedFlask(f: FlaskID) extends PlatformEvent
  // final case class Problem(flask: FlaskID, target: URI, msg: String) extends PlatformEvent
  final case class Unmonitored(flask: FlaskID, target: URI) extends PlatformEvent
  final case object NoOp extends PlatformEvent
}
