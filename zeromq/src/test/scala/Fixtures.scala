package oncue.svc.funnel
package zeromq

object Fixtures {
  def data = large

  def makeBytes(max: Int): Array[Byte] =
    (0 to max).toSeq.map(_.toByte).toArray

  val tiny: Array[Byte] = makeBytes(2)
  val small: Array[Byte] = makeBytes(15)
  val medium: Array[Byte] = makeBytes(150)
  val large: Array[Byte] = makeBytes(1500)
  val megabitInBytes = 125000D

}
