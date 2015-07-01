package funnel
package chemist

import scalaz.concurrent.Task

trait Classifier[A] {
  def task: Task[A => Classification]
}
