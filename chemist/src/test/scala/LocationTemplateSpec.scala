package funnel
package chemist

import org.scalatest._

class LocationTemplateSpec extends FlatSpec with Matchers {
  it should "correctly replace @host" in {
    LocationTemplate("http://@host:5555").build("@host" -> "foo.com"
      ) should equal ("http://foo.com:5555")
  }

  it should "correctly replace @host and @port" in {
    LocationTemplate("http://@host:@port").build("@host" -> "foo.com", "@port" -> "4444"
      ) should equal ("http://foo.com:4444")
  }

  it should "correctly replace @host and ignore @port when not specified" in {
    LocationTemplate("http://@host:@port").build("@host" -> "foo.com"
      ) should equal ("http://foo.com:@port")
  }

  it should "ignore all variables when no replacements are specified" in {
    LocationTemplate("http://@host:@port").build(
      ) should equal ("http://@host:@port")
  }
}

