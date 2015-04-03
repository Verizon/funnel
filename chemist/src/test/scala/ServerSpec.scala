package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import dispatch._, Defaults._

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val platform = new TestPlatform {
    val config = new TestConfig
  }
  val core = new TestChemist

  val http = Http()

  private def fetch(path: String): String =
    Await.result(http(url(s"http://127.0.0.1:64523$path") OK as.String), 5.seconds)

  override def beforeAll(): Unit = {
    Future(Server.unsafeStart(core,platform))
    Thread.sleep(1.seconds.toMillis)
  }

  override def afterAll(): Unit = {
  }

  behavior of "chemist server"

  it should "respond to index.html" in {
    fetch("/index.html").length > 10
  }
}
