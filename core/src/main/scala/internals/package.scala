package funnel

package object internals {
  import java.util.concurrent.atomic.AtomicReference
  import annotation.tailrec

  type Ref[A] = AtomicReference[A]

  implicit class Atomic[A](val atomic: AtomicReference[A]){
    @tailrec final def update(f: A => A): A = {
      val oldValue = atomic.get()
      val newValue = f(oldValue)
      if (atomic.compareAndSet(oldValue, newValue)) newValue else update(f)
    }

    def get: A = atomic.get
  }
}
