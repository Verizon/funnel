package funnel
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,NonEmptyList}
import java.net.URI

class InstanceSpec extends FlatSpec with Matchers {
  def withTags(tags: (String,String)*): String =
    AwsInstance(
      id = "a",
      locations = NonEmptyList(Fixtures.localhost),
      tags = tags.toSeq.toMap
    ).application.get.toString

  it should "extract the right application when tags are present" in {
    withTags(
      "type" -> "foo",
      "revision" -> "1.2.3",
      "aws:cloudformation:stack-name" -> "imdev-foo-1.2.3-Fsf42fx"
    ) should equal ( "foo-1.2.3-Fsf42fx" )
  }

  it should "drop the qualifier if it is not present" in {
    withTags(
      "type" -> "foo",
      "revision" -> "1.2.3"
    ) should equal ( "foo-1.2.3" )

    // to support the old chef crap.
    withTags(
      "Name" -> "service-imdev-contentkey-2-0-5",
      "aws:cloudformation:stack-name" -> "imdev-contentkey-2-0-5"
    ) should equal ( "service-imdev-contentkey-2-0-5-unknown" )
  }

  it should "correctly generate target uris" in {
    AwsInstance(
      id = "a",
      locations = NonEmptyList(Fixtures.localhost),
      tags = Map(
        "type" -> "foo",
        "revision" -> "1.2.3",
        "aws:cloudformation:stack-name" -> "imdev-foo-1.2.3-Fsf42fx")
    ).targets should equal (Set(
      Target("foo-1.2.3-Fsf42fx",URI.create("http://127.0.0.1:5775/stream/previous"),true),
      Target("foo-1.2.3-Fsf42fx",URI.create("http://127.0.0.1:5775/stream/now?kind=traffic"),true)))
  }

}
