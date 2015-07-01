package funnel
package chemist

import scalaz.concurrent.Task

trait Classifier[A] {
  def task: Task[A => Classification]
}

/**
 * The concept here is that upon discovering instances, they
 * are subsequently classified into a setup of finite groups.
 * These groups can then be used for making higher level
 * choices about what an instance should be used for (or if
 * it should be dropped entirely)
 */
sealed trait Classification

object Classification {
  case object ActiveFlask extends Classification
  case object InactiveFlask extends Classification
  case object ActiveTarget extends Classification
  case object ActiveChemist extends Classification
  case object InactiveChemist extends Classification
  case object Unknown extends Classification
}
