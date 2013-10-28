package intelmedia.ws.commons.monitoring

trait Guage[K,A] extends Instrument[K] {
  def modify(f: A => A): Unit
  def set(a: A): Unit = modify(_ => a)
}
