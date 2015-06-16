package funnel
package chemist

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import org.http4s.client._
import org.http4s.Uri
import scalaz.concurrent.Task

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val platform = new TestPlatform {
    val config = new TestConfig
  }
  val core = new TestChemist

  val http = blaze.defaultClient

  private def fetch(path: String): String =
    http(Uri.fromString(s"http://127.0.0.1:64523$path").fold(
      e => sys.error(e.sanitized),
      identity)).flatMap(_.as[String]).runFor(5.seconds)

  override def beforeAll(): Unit = {
    Task(Server.unsafeStart(core,platform)).runAsync(_ => ())
    Thread.sleep(1.seconds.toMillis)
  }

  override def afterAll(): Unit = {
    http.shutdown.run
  }

  behavior of "chemist server"

  it should "respond to index.html" in {
    fetch("/index.html").length > 10
  }
}
