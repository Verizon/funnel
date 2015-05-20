package funnel

package object chemist {
  type HostAndPort = String

  import scalaz.{\/,Order}
  import scalaz.std.string._
  import scalaz.concurrent.Task
  import java.net.URI
  import concurrent.{Future,ExecutionContext}

  implicit val uriOrder: Order[URI] = Order[String].contramap[URI](_.toString)

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Exception](t.get)) }}
}
