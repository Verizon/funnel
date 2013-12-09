package intelmedia.ws.monitoring
package reimann

import com.aphyr.riemann.client.RiemannClient
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.{async,Process}

object Riemann {

  def top(s: String) = s.split("/").headOption.getOrElse(s)
  def tags(s: String) = s.split("/")

  def toEvent(c: RiemannClient)(pt: Datapoint[Any]): Unit = {
    val e = c.event.service(top(pt.key.name))
             .tags(tags(pt.key.name): _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
    (pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(a)
      case b: Boolean => e.state(b.toString)
      case _ => ???
    }).send()
  }

  //def circuitBreak[A](events: Process[Task,Unit])(p: Process[Task,A]):
  //  Process[Task,A] =
  //  events.flatMap { _ =>
  //    p.attempt
  //  }

  /**
   * Mirror all datapoints to the given `RiemannClient`. Returns
   * a thunk that can be used to halt the mirroring.
   */
  def mirrorToRiemann(M: Monitoring)(c: RiemannClient): () => Unit = {
    val alive = async.signal[Unit](Strategy.Executor(Monitoring.defaultPool))
    alive.value.set(())
    alive.continuous.zip(Monitoring.subscribe(M)(_ => true)).evalMap {
      case (_,pt) => Task { toEvent(c)(pt) } (Monitoring.defaultPool)
    }.run.runAsync(_ => ())
    () => alive.value.close
  }

}
