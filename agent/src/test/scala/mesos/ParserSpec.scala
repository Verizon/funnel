package funnel
package agent
package mesos

import funnel.agent.InstrumentKinds._
import org.scalatest.{Matchers, FlatSpec}

/**
 * Created by v765849 on 6/27/15.
 */

class ParserSpec extends FlatSpec with Matchers {

  private def fromFile(name: String): String =
    scala.io.Source.fromURL(
      getClass.getClassLoader.getResource(name)
    ).mkString

  "Import.fetch" should "fetch metric string from url" in {

    val queries = List("master/slaves_disconnected|long")
    val checkfield: String = "master/elected"

    Import.fetch(fromFile("oncue/mesos-statistics.txt"), queries, checkfield) should equal(
      Some(List((ArbitraryMetric("master/slaves_disconnected", GaugeDouble, Some("0"))))))
  }

  "Import.fetch" should "fetch non existent checkfield from url" in {

    val queries = List("master/slaves_disconnected|long")
    val checkfield: String = "master/elected1"

    Import.fetch(fromFile("oncue/mesos-statistics.txt"), queries, checkfield) should equal(
      None)
  }

  "Import.fetch" should "fetch metrics when there is no checkfield" in {

    val queries = List("master/slaves_disconnected|long")
    val checkfield: String = ""

    Import.fetch(fromFile("oncue/mesos-statistics.txt"), queries, checkfield) should equal(
      None)
  }

  "Import.fetch" should "fetch non existent metric string from url" in {

    val queries = List("master/slaves_disconnected1|long")
    val checkfield: String = "master/elected"

    Import.fetch(fromFile("oncue/mesos-statistics.txt"), queries, checkfield) should equal(
      Some(List()))
  }

  "Import.fetch" should "fetch array of fields from url" in {

    val queries = List("master/slaves_disconnected|long","master/messages_deactivate_framework|counter","master/tasks_failed|counter"  )
    val checkfield: String = "master/elected"

    Import.fetch(fromFile("oncue/mesos-statistics.txt"), queries, checkfield) should equal(
      Some(List(ArbitraryMetric("master/slaves_disconnected", GaugeDouble, Some("0")),
        ArbitraryMetric("master/messages_deactivate_framework", Counter, Some("0")),
        ArbitraryMetric("master/tasks_failed", Counter, Some("0"))
      )))
  }


}

