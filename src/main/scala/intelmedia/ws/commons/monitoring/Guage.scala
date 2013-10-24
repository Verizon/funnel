package intelmedia.ws.commons.monitoring

case class Guage[A](modify: (A => A) => Unit) {
  def set(a: A): Unit = modify(_ => a)
}
