package intelmedia.ws.funnel


/**
 * This type is just used for syntax, to pick out a default `Key`
 * from a `Historical`, `Continuous`, or `Periodic`.
 * See the instances in [[intelmedia.ws.funnel.DefaultKeys]].
 * For example, given: {{{
 *   val healthy: Metric[Boolean] = for {
 *     n <- reqs.key
 *     db <- dbOk.key
 *     t <- query.key
 *   } yield n < 20000 &&
 *           db &&
 *           t.mean < 20
 * }}}
 *
 * The call to `key` has the signature:
 *
 * `def key[K2](implicit d: DefaultKey[K,K2]): K2`
 *
 * And picks out the default key.
 */
trait DefaultKey[-A,+B] {
  def apply(a: A): B
}

trait DefaultKeys {

  /** Takes the `previous` key from the input `Historical`. */
  implicit def historical[A]: DefaultKey[Historical[A], Key[A]] =
    new DefaultKey[Historical[A], Key[A]] {
      def apply(a: Historical[A]): Key[A] = a.previous
    }

  /** Takes the `now` key from the input `Continuous`. */
  implicit def continuous[A]: DefaultKey[Continuous[A], Key[A]] =
    new DefaultKey[Continuous[A], Key[A]] {
      def apply(a: Continuous[A]): Key[A] = a.now
    }

  /** Takes the `now` key from the input `Continuous`. */
  implicit def periodic[A]: DefaultKey[Periodic[A], Key[A]] =
    new DefaultKey[Periodic[A], Key[A]] {
      def apply(a: Periodic[A]): Key[A] = a.sliding
    }
}

object DefaultKey extends DefaultKeys
