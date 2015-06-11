package funnel
package chemist

import java.util.Date

sealed abstract class AutoScalingEventKind(val notification: String)
final case object Launch extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_LAUNCH")
final case object LaunchError extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_LAUNCH_ERROR")
final case object Terminate extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_TERMINATE")
final case object TerminateError extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_TERMINATE_ERROR")
final case object TestNotification extends AutoScalingEventKind("autoscaling:TEST_NOTIFICATION")
final case object Unknown extends AutoScalingEventKind("unknown")
object AutoScalingEventKind {
  val list: List[AutoScalingEventKind] =
    List(Launch, LaunchError, Terminate, TerminateError, TestNotification)

  def find(in: String): Option[AutoScalingEventKind] =
    list.find(_.notification.toLowerCase.trim == in.toLowerCase.trim)
}

case class AutoScalingEvent(
  eventId: String,
  kind: AutoScalingEventKind,
  time: Date,
  startTime: Date,
  endTime: Date,
  instanceId: String,
  metadata: Map[String,String] = Map.empty
)
object AutoScalingEvent {
  def apply(i: String, k: AutoScalingEventKind): AutoScalingEvent =
    AutoScalingEvent(
      eventId = "manual",
      kind = k,
      time = new java.util.Date,
      startTime = new java.util.Date,
      endTime = new java.util.Date,
      instanceId = i,
      metadata = Map(
        "cause" -> "operator manually triggered lifecycle operation"
      )
    )
}