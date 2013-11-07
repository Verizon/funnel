package intelmedia.ws.monitoring

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
