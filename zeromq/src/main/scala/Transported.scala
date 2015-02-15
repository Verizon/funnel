package funnel
package zeromq

case class Transported(
  version: Version,
  bytes: Array[Byte]
)

object Transported {
  import Versions._

  def apply(h: String, bytes: Array[Byte]): Transported = {
    val v = for {
      a <- h.split('/').lastOption
      b <- Versions.fromString(a)
    } yield b

    Transported(v.getOrElse(Versions.unknown), bytes)
  }
}
