package funnel
package chemist

import scalaz.{Free,Functor,~>}
import scalaz.concurrent.Task

/**
 * Generic abstraction over natural transformations for free servers in
 * the catagory of Task[A]
 */
abstract class Interpreter[M[+_]: Functor] {
  type F[A] = Free[M, A]

  protected def op[A](r: M[A]): Task[A]

  private val transform: M ~> Task = new (M ~> Task) {
    def apply[A](c: M[A]) = op(c)
  }

  def run[A](f: F[A]): Task[A] =
    f.foldMap(transform)
}
