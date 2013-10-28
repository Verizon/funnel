package intelmedia.ws.commons.monitoring

case class Periodic[+A](now: Key[A], previous: Key[A], sliding: Key[A])
case class Continuous[+A](now: Key[A])
case class Historical[+A](previous: Key[A])
