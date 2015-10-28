package funnel
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers}
import scalaz.{\/,NonEmptyList}
import java.net.URI

class DefaultClassifierSpec extends FlatSpec with Matchers {
  import Classification._

  val f1 = AwsInstance("i-test1", Map(
    AwsTagKeys.name -> "flask",
    AwsTagKeys.qualifier -> "Qdf34",
    AwsTagKeys.version -> "1.1.1",
    AwsTagKeys.mirrorTemplate -> "http://@host:5775"
  ), null)

  val t1 = AwsInstance("i-test1", Map(
    AwsTagKeys.name -> "accounts",
    AwsTagKeys.qualifier -> "xxfSDdf",
    AwsTagKeys.version -> "1.2.1",
    AwsTagKeys.mirrorTemplate -> "http://@host:5775"
  ), null)

  it should "make the right assertions about flasks" in {
    DefaultClassifier.task.run(f1) should equal (ActiveFlask)
  }

  it should "make the right assertions about targets" in {
    DefaultClassifier.task.run(t1) should equal (ActiveTarget)
  }
}