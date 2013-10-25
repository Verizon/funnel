package intelmedia.ws.commons.monitoring

trait Instrument[A] {
  def key: Key[A]
}
