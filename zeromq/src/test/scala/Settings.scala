package funnel
package zeromq

import java.net.URI

object Settings {
  // we use a space in /tmp rather than /var/run because the
  // sbt process does not have permissions to access /var/run
  val uri = new URI(s"ipc:///tmp/funnel.socket")

  val tcp = new URI("tcp://127.0.0.1:7390")
}
