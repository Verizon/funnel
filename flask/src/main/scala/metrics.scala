package funnel
package flask

import funnel.instruments._

object metrics {
  val mirrorDatapoints = counter("mirror/datapoints")
}
