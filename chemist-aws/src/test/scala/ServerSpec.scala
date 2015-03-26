package funnel
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.Await
import scala.concurrent.duration._
import dispatch._, Defaults._

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val core = new AwsChemist

  val platform = new Aws {
    val config = (for {
      a <- defaultKnobs
      b <- knobs.aws.config
    } yield Config.readConfig(a ++ b)).run
  }

  val http = Http()

  private def fetch(path: String): String =
    Await.result(http(url(s"http://127.0.0.1:9000$path") OK as.String), 5.seconds)

  override def beforeAll(): Unit = {
    Server.start(core,platform).runAsync(_ => ())
    Thread.sleep(2.seconds.toMillis)
  }

  override def afterAll(): Unit = {
  }

  behavior of "chemist aws server"

  it should "respond to index.html" in {
    fetch("/index.html").length > 10
  }
}
