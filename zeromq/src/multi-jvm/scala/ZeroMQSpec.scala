package oncue.svc.funnel
package zeromq

import java.net.URI
import scalaz.concurrent.Task
import scalaz.stream.{Channel,Process,io}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import sockets._

class SpecMultiJvmNodeA extends FlatSpec with Matchers {

  lazy val E = Endpoint(pull &&& bind, Location(Settings.uri))

  "receiving streams" should "pull all the sent messages" in {
    Ø.link(E)(Fixtures.signal)(Ø.receive)
      .map(_.toString)
      .to(scalaz.stream.io.stdOut)
      .run.run

    Thread.sleep(5000)

    true should equal (false)
  }
}

class SpecMultiJvmNodeB extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val B = scalaz.std.anyVal.booleanInstance.conjunction

  val E = Endpoint(push &&& connect, Location(Settings.uri))

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
