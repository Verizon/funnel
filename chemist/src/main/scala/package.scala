package oncue.svc.funnel

package object chemist {
  type InstanceID  = String
  type HostAndPort = String

  import scalaz.\/
  import scalaz.concurrent.Task
  import concurrent.{Future,ExecutionContext}

  def printD[A](in: => A): Unit = {
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println(in)
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
  }

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatch(t.get)) }}
}

