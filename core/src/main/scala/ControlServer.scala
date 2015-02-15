package funnel

import java.net.URI

sealed trait Command
case class Mirror(uri: URI, bucket: BucketName) extends Command
case class Discard(uri: URI) extends Command

trait ControlServer {
  import scalaz.stream.Process
  import scalaz.concurrent.Task

  private[funnel] def commands: Process[Task, Command]
}
