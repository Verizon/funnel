package intelmedia.ws.commons.monitoring

trait Counter {
  def incrementBy(by: Int): Unit
  def increment: Unit = incrementBy(1)
  def decrement: Unit = incrementBy(-1)
  def decrementBy(by: Int): Unit = incrementBy(-by)
}
