package intelmedia.ws.funnel

import java.net.URL

sealed trait Command
case class Mirror(url: URL, bucket: BucketName) extends Command
case class Discard(url: URL) extends Command

trait ControlServer {
  import scalaz.stream.Process
  import scalaz.concurrent.Task

  private[funnel] def commands: Process[Task, Command]
}
