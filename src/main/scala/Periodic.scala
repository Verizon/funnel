package intelmedia.ws.commons.monitoring

/**
 * A key set for a periodic metric. Periodic metrics
 * have some window size (say 5 minutes).
 *
 * `now` can be used to lookup the most recent value,
 * `previous` can be used to lookup the most recent
 * value from a full window (for instance, the value
 * for the previous complete 5 minute period), and
 * `sliding` can be used to lookup the value summarizing
 * the last 5 minutes (or whatever the window size)
 * on a rolling basis.
 */
case class Periodic[+A](now: Key[A], previous: Key[A], sliding: Key[A])

/**
 * A key set for a metric defined only in the present.
 * This would be used for a type like `String` for
 * which there isn't a good way of aggregating or
 * summarizing multiple historical values.
 */
case class Continuous[+A](now: Key[A])

// unused
case class Historical[+A](previous: Key[A])
