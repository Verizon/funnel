package oncue.svc.laboratory

import scalaz.concurrent.Task
import Sharding.Target

sealed trait Action
final case class Redistribute(from: InstanceID) extends Action
final case class Redistributed(map: Map[Location, Seq[Target]]) extends Action
final case class AddCapacity(instance: InstanceID) extends Action
final case object NoOp extends Action
