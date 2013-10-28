package intelmedia.ws.commons.monitoring

case class Periods[+A](now: Key[A], previous: Key[A])
case class Now[+A](now: Key[A])
case class Previous[+A](previous: Key[A])
