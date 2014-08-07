package oncue.svc.funnel.chemist

import scalaz.{Free,~>}
import scalaz.concurrent.Task

/**
 * Generic abstraction over natural transformations for free servers in
 * the catagory of Task[A]
 */
trait Interpreter[M[+_]] {
  type F[A] = Free[M, A]

  protected def op[A](r: M[A]): Task[A]

  private val transform: M ~> Task = new (M ~> Task) {
    def apply[A](c: M[A]) = op(c)
  }

  def run[A](f: F[A]): Task[A] =
    f.foldMap(transform)
}
