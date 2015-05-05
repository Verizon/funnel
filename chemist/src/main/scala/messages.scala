package funnel
package chemist

import TargetLifecycle._

sealed trait RepoEvent
object RepoEvent {
  case class StateChange(from: TargetState, to: TargetState, msg: TargetMessage) extends RepoEvent
  case class NewFlask(instance: Instance) extends RepoEvent
  case class Terminated(id: InstanceID) extends RepoEvent
}

trait RepoCommand
object RepoCommand {
  case class Monitor(target: Target) extends RepoCommand
  case class Unmonitor(flask: Flask, target: Target) extends RepoCommand
  case class AssignWork(flask: Instance) extends RepoCommand
  case class ReassignWork(flask: InstanceID) extends RepoCommand
}

trait FlaskCommand
object FlaskCommand {
  case class Monitor(flask: Instance, target: Seq[Target]) extends FlaskCommand
  case class Unmonitor(flask: Instance, target: Seq[Target]) extends FlaskCommand
  case object Report extends FlaskCommand
}

trait FlaskResponse {
  object FlaskResponse {
    case class Monitor(flask: Flask, target: Target) extends FlaskResponse
    case class Unmonitor(flask: Flask, target: Target) extends FlaskResponse
  }
}

