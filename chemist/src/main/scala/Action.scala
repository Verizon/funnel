package funnel
package chemist

import scalaz.concurrent.Task

sealed trait PlatformEvent

object PlatformEvent {
  final case class NewTarget(instance: Instance, targets: List[Target]) extends PlatformEvent
  final case class NewFlask(instance: Instance) extends PlatformEvent
  final case class Terminated(i: InstanceID) extends PlatformEvent
  final case class Monitored(flask: Instance, target: InstanceID) extends PlatformEvent
  final case class Unmonitored(flask: Instance, target: InstanceID) extends PlatformEvent
  final case object NoOp extends PlatformEvent
}
