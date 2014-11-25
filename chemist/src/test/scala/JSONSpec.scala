package oncue.svc.funnel.chemist

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/

class JSONSpec extends FlatSpec with Matchers {
  import JSON._
  import argonaut._, Argonaut._

  it should "serilizes the buckets into JSON" in {
    val A1 = ("test", List(SafeURL("foo.tv"), SafeURL("bar.com")))
    A1.asJson.nospaces should equal ("""{"urls":["foo.tv","bar.com"],"bucket":"test"}""")

    val A2 = List(A1, A1)
    A2.asJson.nospaces should equal (
      """[{"urls":["foo.tv","bar.com"],"bucket":"test"},{"urls":["foo.tv","bar.com"],"bucket":"test"}]""")
  }

  it should "correctly parse the incoming JSON from AWS ASG notifications" in {
    Parse.decodeOption[AutoScalingEvent](Fixtures.asgEvent(Terminate)
      ).get should equal (
        AutoScalingEvent(
          activityId      = "926c4ae3-8181-4668-bcd1-6febc7668d18",
          kind            = Terminate,
          asgName         = "imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
          asgARN          = "arn:aws:autoscaling:us-east-1:465404450664:autoScalingGroup:cf59efeb-6e6e-40c3-90a8-804662f400c7:autoScalingGroupName/imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
          avalibilityZone = "us-east-1b",
          description     = "Terminating EC2 instance: i-dd947af7",
          cause           = "At 2014-07-31T18:30:35Z an instance was taken out of service in response to a EC2 health check indicating it has been terminated or stopped.",
          progress        = 50,
          accountId       = "465404450664",
          time            = "2014-07-31T18:30:41.244Z".asDate,
          startTime       = "2014-07-31T18:30:35.406Z".asDate,
          endTime         = "2014-07-31T18:30:41.244Z".asDate,
          instanceId      = "i-dd947af7"
        )
      )
  }
}
