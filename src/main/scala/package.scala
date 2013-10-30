package intelmedia.ws.commons.monitoring

import scala.concurrent.duration._

package object instruments extends Instruments(5 minutes, Monitoring.default)
