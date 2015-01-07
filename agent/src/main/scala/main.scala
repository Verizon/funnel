package oncue.svc.funnel
package agent

import journal.Logger
import zeromq._

object Main {
  private val log = Logger[Main.type]

  def main(args: Array[String]): Unit = {
    val I = Endpoint(`Pull+Bind`, Address(IPC, host = "/tmp/feeds/0"))
    val O = Endpoint(Publish, Address(TCP, port = Option(7390)))

    new Proxy(I,O).task.runAsync(_.fold(
      e => log.error(s"0mq proxy resulted in failure: $e"),
      _ => ()
    ))
  }
}
