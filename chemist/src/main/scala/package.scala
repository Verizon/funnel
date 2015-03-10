package funnel

package object chemist {
  type InstanceID  = String
  type HostAndPort = String

  import scalaz.\/
  import scalaz.concurrent.Task
  import concurrent.{Future,ExecutionContext}

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Exception](t.get)) }}
}