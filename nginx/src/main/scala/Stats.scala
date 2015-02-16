package oncue.svc.funnel
package nginx

case class Stats(
  connections: Long = 0,
  accepts: Long = 0,
  handled: Long = 0,
  requests: Long = 0,
  reading: Long = 0,
  writing: Long = 0,
  waiting: Long = 0
)
