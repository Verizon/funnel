package intelmedia.ws.funnel
package flask

object Flask {
  import concurrent.duration._

  def instrument(m: Monitoring, S: Sigar): Unit = {
    import S._

    val capacityAvalible: Metric[Boolean] =
      for {
        m <- Mem.usedPercent.key
        mem = m.last.getOrElse(1.0d)
        c <- CPU.Aggregate.usage.total.key
        cpu = c.last.getOrElse(1.0d)
      } yield mem < 85.0 && cpu < 0.8

    capacityAvalible.publishEvery(10.seconds)("capacity-avalible")
    ()
  }
}
