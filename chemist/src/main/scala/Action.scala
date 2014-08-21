package oncue.svc.funnel.chemist

sealed trait Action
final case class Redistribute(from: InstanceID) extends Action
final case class AddCapacity(instance: InstanceID) extends Action
final case object NoOp extends Action
