package intelmedia.ws
package monitoring

import scalaz.\/

object DSL {

  trait Expr {
  }

  object Expr {
    case class Scalar[A](get: A, typeOf: Reportable[A], units: Units[A]) extends Expr
    case class Group(glob: String) extends Expr
    case class Builtin(name: String) extends Expr
    case class Ap(f: Expr, arg: Expr) extends Expr
  }

  trait Decl
  object Decl {
    case class Single(name: String, value: Expr) extends Decl
    case class Group(name: String, subgroup: String, value: Expr) extends Decl
  }
}
