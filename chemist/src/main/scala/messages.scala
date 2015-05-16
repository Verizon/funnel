package funnel
package chemist

import java.net.URI
import scalaz.concurrent.Task
import TargetLifecycle._

sealed trait PlatformEvent

object PlatformEvent {
  final case class NewTarget(target: Target) extends PlatformEvent
  final case class NewFlask(flask: Flask) extends PlatformEvent
  final case class TerminatedTarget(u: URI) extends PlatformEvent
  final case class TerminatedFlask(f: FlaskID) extends PlatformEvent
  final case class Monitored(flask: FlaskID, target: URI) extends PlatformEvent
  final case class Unmonitored(flask: FlaskID, target: URI) extends PlatformEvent
  final case class Assigned(flask: FlaskID, target: Target) extends PlatformEvent
  final case object NoOp extends PlatformEvent
}

sealed trait RepoEvent
object RepoEvent {
  case class StateChange(from: TargetState, to: TargetState, msg: TargetMessage) extends RepoEvent
  case class NewFlask(flask: Flask) extends RepoEvent
  case class TerminatedFlask(id: FlaskID) extends RepoEvent
  case class TerminatedTarget(id: URI) extends RepoEvent
}

trait RepoCommand
object RepoCommand {
  case class Monitor(target: Target) extends RepoCommand
  case class Unmonitor(flask: Flask, target: Target) extends RepoCommand
  case class Telemetry(flask: Flask) extends RepoCommand
  case class AssignWork(flask: Flask) extends RepoCommand
  case class ReassignWork(flask: FlaskID) extends RepoCommand
}

trait FlaskCommand
object FlaskCommand {
  case class Monitor(flask: Flask, target: Seq[Target]) extends FlaskCommand
  case class Unmonitor(flask: Flask, target: Seq[Target]) extends FlaskCommand
  case class Telemetry(flask: Flask) extends FlaskCommand
  case object Report extends FlaskCommand
}

trait FlaskResponse {
  object FlaskResponse {
    case class Monitor(flask: Flask, target: Target) extends FlaskResponse
    case class Unmonitor(flask: Flask, target: Target) extends FlaskResponse
  }
}

