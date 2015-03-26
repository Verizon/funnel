package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.Await
import scala.concurrent.duration._
import dispatch._, Defaults._

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val platform = new TestPlatform {
    val config = new TestConfig
  }
  val core = new TestChemist

  val http = Http()

  private def fetch(path: String): String =
    Await.result(http(url(s"http://127.0.0.1:8080$path") OK as.String), 5.seconds)

  override def beforeAll(): Unit = {
    Server.start(core,platform).runAsync(_ => ())
    Thread.sleep(1.seconds.toMillis)
  }

  override def afterAll(): Unit = {
  }

  behavior of "chemist server"

  // it should "respond to index.html" in {
  //   println(fetch("/index.html"))
  // }

  it must "respond to the /lifecycle/history" in {
    println(fetch("/lifecycle/history"))
  }


}
