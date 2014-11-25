package oncue.svc.funnel.chemist

import com.amazonaws.services.ec2.model.{Instance => EC2Instance}
import com.amazonaws.services.ec2.model.{Placement,Tag}

object Fixtures {

  def instance(
    id: String,
    datacenter: String = "us-east-1b",
    privateDns: String = "foo.internal",
    publicDns: String = "foo.ext.amazonaws.com"
  ): EC2Instance =
    (new EC2Instance).withInstanceId(id)
    .withPlacement(new Placement(datacenter))
    .withPrivateDnsName(privateDns)
    .withPublicDnsName(publicDns)
    .withTags(Seq("baz" -> "v1","bar" -> "v2").map { case (k,v) => new Tag(k,v) }:_*)

  val instances: Seq[EC2Instance] =
    instance("i-dx947af7") ::
    instance("i-15807647") :: Nil

  def asgEvent(kind: AutoScalingEventKind): String = s"""
    |{
    |  "StatusCode": "InProgress",
    |  "Service": "AWS Auto Scaling",
    |  "AutoScalingGroupName": "test-group",
    |  "Description": "test",
    |  "ActivityId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "Event": "${kind.notification}",
    |  "Details": {
    |    "Availability Zone": "us-east-1b"
    |  },
    |  "AutoScalingGroupARN": "...",
    |  "Progress": 50,
    |  "Time": "2014-07-31T18:30:41.244Z",
    |  "AccountId": "465404450664",
    |  "RequestId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "StatusMessage": "",
    |  "EndTime": "2014-07-31T18:30:41.244Z",
    |  "EC2InstanceId": "i-dd947af7",
    |  "StartTime": "2014-07-31T18:30:35.406Z",
    |  "Cause": "At 2014-07-31T18:30:35Z ..."
    |}
    """.stripMargin
}
