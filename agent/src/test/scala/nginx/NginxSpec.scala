package oncue.svc.funnel
package agent
package nginx

import org.scalatest.{FlatSpec,Matchers}

class NginxSpec extends FlatSpec with Matchers {

  private def fromFile(name: String): String =
    scala.io.Source.fromURL(
      getClass.getClassLoader.getResource(name)
        ).mkString

  // "nginx parser" should "parse response-1.txt" in {
  //   println {
  //     Nginx.parse(fromFile("oncue/response-1.txt"))
  //   }
  // }

  "activeR" should "parse the active connections line" in {
    val Nginx.activeR(o) = "Active connections: 1"
    o should equal ("1")
  }

  "handledR" should "parse the handled line" in {
    val Nginx.handledR(a,b,c) = " 5 6 7"
    a should equal ("5")
    b should equal ("6")
    c should equal ("7")
  }

  "currentR" should "parse the current line" in {
    val Nginx.currentR(a,b,c) = "Reading: 0 Writing: 1 Waiting: 9"
    a should equal ("0")
    b should equal ("1")
    c should equal ("9")
  }

}
