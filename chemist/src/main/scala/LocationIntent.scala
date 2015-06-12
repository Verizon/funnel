package funnel
package chemist

/**
 * Given locations are modeling where a monitorable thing resides,
 * and it `Location` is somewhat overloaded as its used for both
 * admin channels and mirroring channels, `LocationIntent` serves
 * as a discriminator for sequences of Locations.
 */
sealed trait LocationIntent

object LocationIntent {
  def fromString(s: String): Option[LocationIntent] =
    all.find(_.toString.toLowerCase == s.toLowerCase)

  lazy val all = List(Mirroring, Supervision)

  case object Mirroring extends LocationIntent
  case object Supervision extends LocationIntent
}

// sealed trait MirrorMode {
//   def protocol: String
//   def port: Int
// }
// object MirrorMode {
//   def fromString(in: String): Option[MirrorMode] =
//     in.toLowerCase.trim match {
//       case "zeromq" => Some(ZeroMQ)
//       case "http"   => Some(Http)
//       case _        => None
//     }

//   case object ZeroMQ extends MirrorMode {
//     val protocol = "tcp"
//     val port = 7390
//   }
//   case object Http extends MirrorMode {
//     val protocol = "http"
//     val port = 5775
//   }
// }
