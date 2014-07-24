package intelmedia.ws.funnel

trait ControlServer {
  import scalaz.stream.Process
  import scalaz.concurrent.Task
  import java.net.URL

  private[funnel] def sourcesToMirror: Process[Task, (URL, String)]
  private[funnel] def sourcesToTerminate: Process[Task, URL]
}
