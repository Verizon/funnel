package oncue.svc.funnel
package agent
package nginx

import util.matching.Regex
import scalaz.\/
import journal.Logger

object Nginx {

  private val log = Logger[Nginx.type]

  case class Stats(
    connections: Long = 0,
    accepts: Long = 0,
    handled: Long = 0,
    requests: Long = 0,
    reading: Long = 0,
    writing: Long = 0,
    waiting: Long = 0
  )

  private[nginx] val activeR = """^Active connections:\s+(\d+)""".r
  private[nginx] val handledR = """^\s+(\d+)\s+(\d+)\s+(\d+)""".r
  private[nginx] val currentR = """^Reading:\s+(\d+).*Writing:\s+(\d+).*Waiting:\s+(\d+)""".r

  def parse(input: String): Throwable \/ Stats = \/.fromTryCatchNonFatal {
    val Array(line1, line2, line3) = input.split('\n')

    log.debug(s"line 1 = $line1")
    log.debug(s"line 2 = $line2")
    log.debug(s"line 3 = $line3")

    val activeR(connections)                 = line1
    // val handledR(accepts, handled, requests) = line2
    // val currentR(reading, writing, waiting)  = line3

    Stats(
      connections.toLong)//,
      // accepts.toLong,
      // handled.toLong,
      // requests.toLong,
      // reading.toLong,
      // writing.toLong,
      // waiting.toLong)
  }

}
