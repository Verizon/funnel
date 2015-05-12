package funnel
package chemist

import java.net.URI
import scalaz.{\/,\/-,-\/}

trait Instance {
  def id: String
  def location: Location
  def telemetryLocation: Location
  def application: Option[Application]
  def asURI: URI = location.asURI()
  def targets: Set[Target]
  def asFlask: Flask
}
