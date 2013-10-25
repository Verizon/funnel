package intelmedia.ws.commons.monitoring

trait Guage[A] extends Instrument[A] {
  def modify(f: A => A): Unit
  def set(a: A): Unit = modify(_ => a)
}
