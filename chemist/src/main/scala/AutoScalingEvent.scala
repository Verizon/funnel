package oncue.svc.funnel.chemist

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
  activityId: String,
  kind: AutoScalingEventKind,
  asgName: String,
  asgARN: String,
  avalibilityZone: String,
  description: String,
  cause: String,
  progress: Int,
  accountId: String,
  time: Date,
  startTime: Date,
  endTime: Date,
  instanceId: String
)
object AutoScalingEvent {
  def apply(i: String, k: AutoScalingEventKind): AutoScalingEvent =
    AutoScalingEvent(
      activityId = "manual",
      kind = k,
      asgName = "unknown",
      asgARN = "unknown",
      avalibilityZone = "unknown",
      description = "unknown",
      cause = "operator manually triggered lifecycle operation",
      progress = 50,
      accountId = "unknown",
      time = new java.util.Date,
      startTime = new java.util.Date,
      endTime = new java.util.Date,
      instanceId = i
    )
}