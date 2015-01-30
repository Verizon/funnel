package oncue.svc.funnel
package zeromq

object Settings {
  // we use a space in /tmp rather than /var/run because the
  // sbt process does not have permissions to access /var/run
  val socket = "/tmp/funnel.socket"

  val uri = new java.net.URI(s"ipc://$socket")
}
