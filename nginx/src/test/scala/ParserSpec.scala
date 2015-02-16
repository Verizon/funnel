package oncue.svc.funnel
package nginx

import org.scalatest.{FlatSpec,Matchers}
import scalaz.syntax.either._

class ParserSpec extends FlatSpec with Matchers {

  private def fromFile(name: String): String =
    scala.io.Source.fromURL(
      getClass.getClassLoader.getResource(name)
        ).mkString

  behavior of "parse"

  it should "parse response-1.txt" in {
    Parser.parse(fromFile("oncue/response-1.txt")) should equal (
      Stats(1,43,44,45,0,1,2).right)
  }

  it should "parse response-2.txt" in {
    Parser.parse(fromFile("oncue/response-2.txt")) should equal (
      Stats(0,100,111,222,7,6,5).right)
  }

  it should "parse response-3.txt" in {
    Parser.parse(fromFile("oncue/response-3.txt")).isLeft should equal (true)
  }

  "activeR" should "parse the active connections line" in {
    val Parser.activeR(o) = "Active connections: 1 "
    o should equal ("1")
  }

  "handledR" should "parse the handled line" in {
    val Parser.handledR(a,b,c) = " 5 6 7 "
    a should equal ("5")
    b should equal ("6")
    c should equal ("7")
  }

  "currentR" should "parse the current line" in {
    val Parser.currentR(a,b,c) = "Reading: 0 Writing: 1 Waiting: 9 "
    a should equal ("0")
    b should equal ("1")
    c should equal ("9")
  }

}
