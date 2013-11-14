package intelmedia.ws.monitoring

import scala.concurrent.duration._

package object instruments extends Instruments(5 minutes, Monitoring.default) with DefaultKeys {
  JVM.instrument(this)
  Clocks.instrument(this)
}
