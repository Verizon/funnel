package funnel
package chemist

import zeromq.Protocol

/**
 * NetworkScheme represents the mechinism that will be used to actually
 * fetch metrics from a given `Target`. Most URIs have a single, opqauqe
 * URI scheme (see rfc2396), its legal to encode specilizations using the
 * '+' character. Subsequently, we encode the ZMTP "mode" specilization
 * in this manner. Examples are:
 *
 * `zmtp+tcp`
 * `http`
 * `zmtp+ipc`
 *
 * Doing this ensures that if we add a different transport system at a later
 * date we did not totally occupy the TCP namespace w/r/t URI declerations.
 */
sealed trait NetworkScheme {
  def scheme: String
  override def toString: String = scheme
}

object NetworkScheme {
  val all: Seq[NetworkScheme] = Protocol.all.map(Zmtp(_)) :+ Http

  def fromString(in: String): Option[NetworkScheme] =
    all.find(_.scheme == in.toLowerCase.trim)

  case class Zmtp(p: Protocol) extends NetworkScheme {
    val scheme = s"zmtp+${p.toString}"
  }
  case object Http extends NetworkScheme {
    val scheme = "http"
  }
}
