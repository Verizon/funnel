package oncue.svc.funnel
package zeromq

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import java.net.URI
import scalaz.stream.Process
import scalaz.concurrent.Task

class SpecMultiJvmNodeA extends FlatSpec with Matchers {
  import scalaz.stream.io
  import scalaz.stream.Channel

  lazy val E = Endpoint(`Pull+Bind`, Location(Settings.uri))

  "receiving streams" should "pull all the sent messages" in {
    Ø.link(E)(Ø.monitoring.alive)(Ø.receive)
      .map(_.toString)
      .to(scalaz.stream.io.stdOut)
      .run.run

    Thread.sleep(5000)

    true should equal (false)
  }
}

class SpecMultiJvmNodeB extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val B = scalaz.std.anyVal.booleanInstance.conjunction

  val E = Endpoint(`Push+Connect`, Location(Settings.uri))

  val seq: Seq[Array[Byte]] = for(i <- 0 to 10000) yield Fixtures.data
  val k: Seq[Boolean] = seq.map(_ => true) ++ Seq(false)
  // stupid scalac cant handle this in-line.
  val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)
  val alive: Process[Task, Boolean] = Process.emitAll(k)

  "publishing streams" should "send the entire fixture set" in {
    val result: Boolean = Ø.linkP(E)(alive)(socket =>
      proc.through(Ø.write(socket))).runFoldMap(identity).run

    result should equal (true)
  }
}
