package funnel
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/

class JsonSpec extends FlatSpec with Matchers {
  import chemist.JSON._
  import aws.JSON._
  import argonaut._, Argonaut._

  it should "correctly parse the incoming JSON from AWS ASG notifications" in {
    Parse.decodeOption[AutoScalingEvent](Fixtures.asgEvent(Terminate, name = "foo", instanceId = "i-dd947af7")
      ).get should equal (
        AutoScalingEvent(
          activityId      = "926c4ae3-8181-4668-bcd1-6febc7668d18",
          kind            = Terminate,
          asgName         = "foo",
          asgARN          = "...",
          avalibilityZone = "us-east-1b",
          description     = "test",
          cause           = "At 2014-07-31T18:30:35Z ...",
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
