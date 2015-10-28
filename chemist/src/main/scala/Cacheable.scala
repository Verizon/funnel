package funnel
package chemist

import journal.Logger
import Chemist.Context
import scalaz.concurrent.Task

/**
 * a convenience typeclass that allows us to re-use the same
 * caching sink without having to specilize on how we cache
 * various different types at different stages in the pipeline.
 */
trait Cacheable[A]{
  def cache(a: Context[A], to: StateCache): Task[Unit]
}
object Cacheable {
  import scalaz.syntax.apply._

  private[this] val log = Logger[Cacheable.type]

  implicit val planCachable: Cacheable[Plan] =
    new Cacheable[Plan]{
      def cache(in: Context[Plan], to: StateCache): Task[Unit] =
        for {
          _ <- to.plan(in.value)
          _ <- to.distribution(in.distribution)
        } yield ()
    }

  implicit val eventCachable: Cacheable[PlatformEvent] =
    new Cacheable[PlatformEvent]{
      def cache(in: Context[PlatformEvent], to: StateCache): Task[Unit] =
        to.event(in.value)
    }
}
