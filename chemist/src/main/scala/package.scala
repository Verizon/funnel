package oncue.svc.funnel

package object chemist {
  import java.util.concurrent.atomic.AtomicReference
  import annotation.tailrec
  import scalaz.==>>

  type InstanceID = String
  type Host       = String
  type Ref[A]     = AtomicReference[A]
  type InstanceM  = InstanceID ==>> Instance

  implicit class Atomic[A](val atomic: AtomicReference[A]){
    @tailrec final def update(f: A => A): A = {
      val oldValue = atomic.get()
      val newValue = f(oldValue)
      if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
    }

    def get: A = atomic.get
  }

  import scalaz.\/
  import scalaz.concurrent.Task
  import concurrent.{Future,ExecutionContext}

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatch(t.get)) }}

}

