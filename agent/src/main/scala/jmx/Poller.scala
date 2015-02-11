package oncue.svc.funnel
package agent
package jmx

import cjmx.util.jmx.{Attach,JMXTags}
import journal.Logger
import scalaz.stream.{Process,Channel,io}
import scalaz.concurrent.Task
import javax.management.remote.JMXConnector
import java.util.concurrent.ConcurrentHashMap

case class JMXImportException(override val getMessage: String) extends RuntimeException

object Import {
  private[this] val log = Logger[Import.type]

  type VMID = String
  type ConnectorCache = ConcurrentHashMap[VMID, JMXConnector]

  def localVMs: Process[Task, VMID] =
    Process.emitAll(Attach.localVMs.map(_.id))

  /**
   * We dont want to constantly thrash making connections to other VMs on the same host
   * so instead we will keep some state around about the connectors that are already avalible
   * and use those instead. It's basically a cache to avoid needless re-evaluation.
   */
  def loadConnector(state: ConnectorCache): Channel[Task, VMID, JMXConnector] =
    io.channel { vmid =>
        if(state.containsKey(vmid)) Task.now(state.get(vmid))
        else {
          for {
            a <- Attach.localConnect(vmid).fold(e => Task.fail(JMXImportException(e)), Task.now)
            b <- Task.delay(state.putIfAbsent(vmid, a))
        } yield b
      }
    }

  def foo(cache: ConnectorCache): Process[Task, JMXConnector] = {
    localVMs.through(loadConnector(cache))
  }

  // def periodicly(from: Seq[VMID])(frequency: Duration = 10.seconds): Process[Task,Unit] =
// Process.awakeEvery(frequency)(Strategy.Executor(serverPool), schedulingPool
      // )


}
