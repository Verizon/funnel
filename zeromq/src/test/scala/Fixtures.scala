package funnel
package zeromq

import scalaz.concurrent._

object Fixtures {
  def data = large

  def makeBytes(max: Int): Array[Byte] =
    (0 to max).toSeq.map(_.toByte).toArray

  val tiny: Array[Byte] = makeBytes(2)
  val small: Array[Byte] = makeBytes(15)
  val medium: Array[Byte] = makeBytes(150)
  val large: Array[Byte] = makeBytes(1500)
  val megabitInBytes = 125000D

  val S = scalaz.concurrent.Strategy.Executor(Monitoring.defaultPool)

  val signal = scalaz.stream.async.signalOf[Boolean](true)(S)
}
