package intelmedia.ws.funnel
package chemist

sealed trait Action
case object Foo extends Action
case object DistributeLoad extends Action
case object AddCapacity extends Action
case object NoOp extends Action
