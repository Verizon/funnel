package oncue.svc.funnel.chemist

import com.amazonaws.services.ec2.model.{Instance => EC2Instance}
import com.amazonaws.services.ec2.model.{Placement,Tag}

object Fixtures {

  def instance(id: String): EC2Instance =
    (new EC2Instance).withInstanceId(id)
    .withPlacement(new Placement("us-east-1b"))
    .withPrivateDnsName("foo.internal")
    .withPublicDnsName("foo.ext.amazonaws.com")
    .withTags(Seq("baz" -> "v1","bar" -> "v2").map { case (k,v) => new Tag(k,v) }:_*)

  val instances: Seq[EC2Instance] =
    instance("i-dx947af7") ::
    instance("i-15807647") :: Nil

  val asgEventJson1 = """
    |{
    |  "StatusCode": "InProgress",
    |  "Service": "AWS Auto Scaling",
    |  "AutoScalingGroupName": "imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
    |  "Description": "Terminating EC2 instance: i-dd947af7",
    |  "ActivityId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "Event": "autoscaling:EC2_INSTANCE_TERMINATE",
    |  "Details": {
    |    "Availability Zone": "us-east-1b"
    |  },
    |  "AutoScalingGroupARN": "arn:aws:autoscaling:us-east-1:465404450664:autoScalingGroup:cf59efeb-6e6e-40c3-90a8-804662f400c7:autoScalingGroupName/imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
    |  "Progress": 50,
    |  "Time": "2014-07-31T18:30:41.244Z",
    |  "AccountId": "465404450664",
    |  "RequestId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "StatusMessage": "",
    |  "EndTime": "2014-07-31T18:30:41.244Z",
    |  "EC2InstanceId": "i-dd947af7",
    |  "StartTime": "2014-07-31T18:30:35.406Z",
    |  "Cause": "At 2014-07-31T18:30:35Z an instance was taken out of service in response to a EC2 health check indicating it has been terminated or stopped."
    |}
    """.stripMargin
}
