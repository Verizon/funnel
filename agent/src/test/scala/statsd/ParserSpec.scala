package funnel
package agent
package statsd

import org.scalatest._
import scalaz.\/, \/._
import InstrumentKinds._

class ParserSpec extends FlatSpec with Matchers {
  "Parser.toMetric" should "interpret metric strings" in {

    Parser.toMetric("foo:123|g") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )

    Parser.toMetric("bar:567|c") should equal (
      right(ArbitraryMetric("bar",Counter,Some("567.0"))) )

    Parser.toMetric("bar:567|c|@2.0") should equal(
      right(ArbitraryMetric("bar",Counter,Some("283.5"))) )

    Parser.toMetric("bar:xxxx|c|@2.0").isLeft should equal(true)

    Parser.toMetric("bar:1234|c") should equal(
      right(ArbitraryMetric("bar",Counter,Some("1234.0"))) )

    Parser.toMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage:0.01|g") should equal(
      right(ArbitraryMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage",GaugeDouble,Some("0.01"))) )

    Parser.toMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage:234.23456|g") should equal(
      right(ArbitraryMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage",GaugeDouble,Some("234.23"))) )
  }
}
