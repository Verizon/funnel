package funnel
package zeromq

import java.net.URI
import java.util.concurrent.{ExecutorService,ScheduledExecutorService}
import scalaz.stream.async.mutable.Signal
import scalaz.stream.{Process,Process1}
import scalaz.concurrent.Task

object Mirror {
  import sockets._

  def from(alive: Signal[Boolean], discriminator: List[Array[Byte]] = Nil)(uri: URI): Process[Task, Datapoint[Any]] = {
    val t = if(discriminator.isEmpty) topics.all else topics.specific(discriminator)
    Endpoint(subscribe &&& (connect ~ t), uri).fold(Process.fail(_), l =>
      Ø.link(l)(alive)(Ø.receive).pipe(fromTransported)
    )
  }

  val fromTransported : Process1[Transported, Datapoint[Any]] = Process.receive1 { (t:Transported) =>
    import http.JSON._, http.SSE
    t.version match {
      case Versions.v1 =>
        try Process.emit(SSE.parseOrThrow[Datapoint[Any]](new String(t.bytes)))
        catch {
          case e: Exception => Process.fail(e)
        }
      case Versions.v2 => sys.error("not implemented yet!")
    }
  } ++ fromTransported
/*
  // fairly ugly hack, but it works for now
  private[zeromq] def fromTransported(t: Transported): Process[Task, Datapoint[Any]] = {
    import http.JSON._, http.SSE
    t.version match {
      case Versions.v1 =>
        try Process.emit(SSE.parseOrThrow[Datapoint[Any]](new String(t.bytes)))
        catch {
          case e: Exception => Process.fail(e)
        }
      case Versions.v2 => sys.error("not implemented yet!")
    }
  }
 */
}
