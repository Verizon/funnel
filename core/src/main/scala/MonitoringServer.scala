package intelmedia.ws.funnel

trait ControlServer {
  import scalaz.stream.Process
  import scalaz.concurrent.Task
  import java.net.URL

  private[funnel] def mirroringSources: Process[Task, (URL, String)]
}
