package intelmedia.ws.funnel
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
  activityId: String,
  event: AutoScalingEventKind,
  asgName: String,
  asgARN: String,
  avalibilityZone: String,
  description: String,
  cause: String,
  progress: Int,
  accountId: String,
  time: Date,
  startTime: Date,
  endTime: Date
)

object JSON {
  import argonaut._, Argonaut._
  import javax.xml.bind.DatatypeConverter // hacky, but saves the extra dependencies

  implicit val JsonToAutoScalingEventKind: DecodeJson[AutoScalingEventKind] =
    DecodeJson(c => for {
      a <- (c --\ "Event").as[String]
    } yield AutoScalingEventKind.find(a).getOrElse(Unknown))

  implicit def JsonToJavaDate(name: String): DecodeJson[Date] =
    DecodeJson(c => (c --\ name).as[String].map(DatatypeConverter.parseDateTime(_).getTime))

  /**
   * {
   *   "StatusCode": "InProgress",
   *   "Service": "AWS Auto Scaling",
   *   "AutoScalingGroupName": "imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
   *   "Description": "Terminating EC2 instance: i-dd947af7",
   *   "ActivityId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
   *   "Event": "autoscaling:EC2_INSTANCE_TERMINATE",
   *   "Details": {
   *     "Availability Zone": "us-east-1b"
   *   },
   *   "AutoScalingGroupARN": "arn:aws:autoscaling:us-east-1:465404450664:autoScalingGroup:cf59efeb-6e6e-40c3-90a8-804662f400c7:autoScalingGroupName/imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
   *   "Progress": 50,
   *   "Time": "2014-07-31T18:30:41.244Z",
   *   "AccountId": "465404450664",
   *   "RequestId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
   *   "StatusMessage": "",
   *   "EndTime": "2014-07-31T18:30:41.244Z",
   *   "EC2InstanceId": "i-dd947af7",
   *   "StartTime": "2014-07-31T18:30:35.406Z",
   *   "Cause": "At 2014-07-31T18:30:35Z an instance was taken out of service in response to a EC2 health check indicating it has been terminated or stopped."
   * }
   */
  implicit val JsonToAutoScalingEvent: DecodeJson[AutoScalingEvent] =
    DecodeJson(input => for {
      a <- (input --\ "ActivityId").as[String]
      b <- JsonToAutoScalingEventKind(input)
      c <- (input --\ "AutoScalingGroupName").as[String]
      d <- (input --\ "AutoScalingGroupARN").as[String]
      e <- (input --\ "Details" --\ "Availability Zone").as[String]
      f <- (input --\ "Description").as[String]
      g <- (input --\ "Cause").as[String]
      h <- (input --\ "Progress").as[Int]
      i <- (input --\ "AccountId").as[String]
      j <- JsonToJavaDate("Time")(input)
      k <- JsonToJavaDate("StartTime")(input)
      l <- JsonToJavaDate("EndTime")(input)
    } yield AutoScalingEvent(
      activityId      = a,
      event           = b,
      asgName         = c,
      asgARN          = d,
      avalibilityZone = e,
      description     = f,
      cause           = g,
      progress        = h,
      accountId       = i,
      time            = j,
      startTime       = k,
      endTime         = l
    ))

}