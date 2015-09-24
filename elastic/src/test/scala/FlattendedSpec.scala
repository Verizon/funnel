package funnel
package elastic

import org.scalatest.{FlatSpec,Matchers}

class FlattenedSpec extends FlatSpec with Matchers {
  val F = ElasticFlattened(Monitoring.default)

  def point =
    Datapoint[Any](Key[Double](s"now/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), 3.14)

  def point2 =
    Datapoint[Any](Key[Stats](s"sliding/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))

  def point3 =
    Datapoint[Any](Key[Stats](s"sliding/file_system.%2F.use_percent", Units.Bytes(Units.Base.Kilo), "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))

  // TIM: christ, we need more tests here. sorry!

  "toJson" should "correctly render documents to json in the happy case" in {
    println {
      F.toJson("dev", "127.0.0.1", "local")(point3)
    }
  }
}