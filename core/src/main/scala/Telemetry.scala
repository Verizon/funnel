
package funnel

import java.net.URI

sealed trait Telemetry

final case class Error(names: Names) extends Telemetry {
  override def toString = s"${names.mine} gave up on ${names.kind} server ${names.theirs}"
}

final case class NewKey(key: Key[_]) extends Telemetry
final case class Monitored(i: URI) extends Telemetry
final case class Unmonitored(i: URI) extends Telemetry
final case class Problem(i: URI, msg: String) extends Telemetry

