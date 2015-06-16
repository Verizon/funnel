package funnel
package chemist

import scalaz.{\/,-\/,\/-}
import java.net.URI
import LocationIntent._

/**
 * Represents the combination of scheme, host and port
 * as a single unit. It is entirely feasible that a single
 * machine host has multiple `Location` instances. Moreover,
 * a `Location` has a minimum of one URI, which represents
 * the canonical network location of this specific scheme/host/port
 * which can be used for network validation etc. In addition,
 * a `Location` might have a selection of templated path URIs, but
 * this is entirely optional.
 */
case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: NetworkScheme = NetworkScheme.Http,
  isPrivateNetwork: Boolean = true,
  intent: LocationIntent,
  templates: Seq[LocationTemplate] = Seq.empty
){
  def templatedPathURIs: Seq[URI] =
    templates.map(uriFromTemplate)

  def uri: URI =
    URI.create(s"${protocol.toString}://$host:$port")

  def uriFromTemplate(t: LocationTemplate): URI =
    URI.create(t.build(
      "@protocol" -> protocol.scheme,
      "@host"     -> host,
      "@port"     -> port.toString
    ))
}

object Location {
  def fromURI(
    uri: URI,
    dc: String,
    int: LocationIntent,
    tmp: Map[NetworkScheme, Seq[LocationTemplate]]
  ): Option[Location] =
    for {
      a <- Option(uri.getHost)
      b <- Option(uri.getPort)
      c <- NetworkScheme.fromString(uri.getScheme)
      d <- tmp.get(c).orElse(Some(Seq.empty[LocationTemplate]))
    } yield Location(
      host = a,
      port = b,
      protocol = c,
      datacenter = dc,
      intent = int,
      templates = d)
}
