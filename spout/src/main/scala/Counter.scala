package intelmedia.ws.monitoring

trait Counter[K] extends Instrument[K] {
  def incrementBy(by: Int): Unit
  def increment: Unit = incrementBy(1)
  def decrement: Unit = incrementBy(-1)
  def decrementBy(by: Int): Unit = incrementBy(-by)
}
