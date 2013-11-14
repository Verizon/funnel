package intelmedia.ws.monitoring

import scala.concurrent.duration._
import scala.language.higherKinds
import scalaz.{~>, Monad}

trait Metric[+A] {
  import Metric._

  def flatMap[B](f: A => Metric[B]): Metric[B] = this match {
    case Pure(a) => f(a)
    case Bind(k, g) => Bind(k, g andThen (_ flatMap f))
  }

  def map[B](f: A => B): Metric[B] = flatMap(a => Pure(f(a)))

  private[monitoring] def run[F[+_]](f: Key ~> F)(implicit F: Monad[F]): F[A] =
    Metric.run(this)(f)
}

object Metric extends scalaz.Monad[Metric] {

  case class Pure[A](get: A) extends Metric[A]
  case class Bind[A,B](key: Key[A], f: A => Metric[B]) extends Metric[B]

  def key[A](k: Key[A]): Metric[A] =
    Bind(k, (a: A) => Pure(a))

  def point[A](a: => A): Metric[A] = Pure(a)

  def bind[A,B](m: Metric[A])(f: A => Metric[B]): Metric[B] =
    m flatMap f

  /**
   * The interpreter for `Metric`. We can convert `Metric[A]` to any
   * `F[A]`, given an 'interpreter' for keys, the natural transformation
   * `Key ~> F` (which is the Scala encoding of `forall A . Key[A] => F[A]`).
   */
  def run[F[_],A](m: Metric[A])(f: Key ~> F)(implicit F: Monad[F]): F[A] =
    m match {
      case Pure(a) => F.point(a)
      case Bind(k, g) => F.bind(f(k))(e => run(g(e))(f))
    }

  /** Infix syntax for `Metric`. */
  implicit class MetricSyntax[A](self: Metric[A]) {
    import Events.Event

    // variance issues prevent us from putting these directly on `Metric`

    /** Publish this `Metric` to `M` whenever `ticks` emits a value. */
    def publish(ticks: Event)(label: String, units: Units[A] = Units.None)(
                implicit reportable: A => Reportable[A],
                M: Monitoring = Monitoring.default): Key[A] =
      M.publish(label, units)(ticks(M))(self)

    /** Publish this `Metric` to `M` every `d` elapsed time. */
    def publishEvery(d: Duration)(label: String, units: Units[A] = Units.None)(
                     implicit reportable: A => Reportable[A],
                     M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.every(d))(label, units)

    /** Publish this `Metric` to `M` when `k` is updated. */
    def publishOnChange(k: Key[Any])(label: String, units: Units[A] = Units.None)(
                        implicit reportable: A => Reportable[A],
                        M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.changed(k))(label, units)

    /** Publish this `Metric` to `M` when either `k` or `k2` is updated. */
    def publishOnChanges(k: Key[Any], k2: Key[Any])(
                         label: String, units: Units[A] = Units.None)(
                         implicit reportable: A => Reportable[A],
                         M: Monitoring = Monitoring.default): Key[A] =
      publish(Events.or(Events.changed(k), Events.changed(k2)))(label, units)
  }
}
