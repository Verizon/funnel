package intelmedia.ws.monitoring

trait Guage[K,A] extends Instrument[K] {
  def set(a: A): Unit
}

object Guage {

  def scale[K](k: Double)(g: Guage[K,Double]): Guage[K,Double] =
    new Guage[K,Double] {
      def set(d: Double): Unit =
        g.set(d * k)
      def keys = g.keys
    }
}
