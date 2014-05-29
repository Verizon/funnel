package intelmedia.ws.funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._

/** Functions for adding various system metrics collected by SIGAR to a `Monitoring` instance. */
object Sigar {

  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 3 seconds): Unit = {
    val sigar = new org.hyperic.sigar.Sigar
    import I._
    type G = Gauge[Periodic[Stats],Double]

    Process.awakeEvery(t)(ES, TS).map { _ =>
      import org.hyperic.sigar.{Cpu, CpuPerc}

      def cpuTime(cpu: Cpu, m: Map[String, G]) = {
        m("user").set(cpu.getUser)
        m("idle").set(cpu.getIdle)
        m("total").set(cpu.getTotal)
        m("nice").set(cpu.getNice)
        m("irq").set(cpu.getIrq)
        m("sys").set(cpu.getSys)
        m("waiting").set(cpu.getWait)
        m("softirq").set(cpu.getSoftIrq)
        m("stolen").set(cpu.getStolen)
      }
      def cpuUsage(cpu: CpuPerc, m: Map[String, G]) = {
        m("user").set(cpu.getUser)
        m("idle").set(cpu.getIdle)
        m("combined").set(cpu.getCombined)
        m("nice").set(cpu.getNice)
        m("irq").set(cpu.getIrq)
        m("sys").set(cpu.getSys)
        m("waiting").set(cpu.getWait)
        m("softirq").set(cpu.getSoftIrq)
        m("stolen").set(cpu.getStolen)
      }

      // Add aggregate CPU timings
      cpuTime(sigar.getCpu, CPU.Aggregate.time)

      // Add aggregate CPU usage
      cpuUsage(sigar.getCpuPerc, CPU.Aggregate.usage)

      // Add per-CPU timings
      sigar.getCpuList.zip(CPU.Individual.times).foreach { case (cpu, gauges) =>
        cpuTime(cpu, gauges)
      }

      // Add per-CPU usages
      sigar.getCpuPercList.zip(CPU.Individual.usages).foreach { case (cpu, gauges) =>
        cpuUsage(cpu, gauges)
      }

    }.run.runAsync(_ => ())

    object CPU {
      def label(s: String) = s"system/cpu/$s"

      def cpuTime(label: String => String): Map[String, G] = {
        def ms(s: String) = numericGauge(label(s), 0.0, Units.Milliseconds)

        Map("user" -> ms("user"),
            "idle" -> ms("idle"),
            "total" -> ms("total"),
            "nice" -> ms("nice"),
            "irq" -> ms("irq"),
            "sys" -> ms("sys"),
            "waiting" -> ms("wait"),
            "softirq" -> ms("softirq"),
            "stolen" -> ms("stolen"))
      }

      def cpuUsage(label: String => String): Map[String, G] = {
        def pct(s: String) = numericGauge(label(s), 0.0, Units.Ratio)

        Map("user" -> pct("user"),
            "idle" -> pct("idle"),
            "total" -> pct("combined"),
            "nice" -> pct("nice"),
            "irq" -> pct("irq"),
            "sys" -> pct("sys"),
            "waiting" -> pct("wait"),
            "softirq" -> pct("softirq"),
            "stolen" -> pct("stolen"))
      }

      object Aggregate {
        def label(s: String) = CPU.label(s"aggregate/$s")

        val time = cpuTime(s => label(s"time/$s"))
        val usage = cpuUsage(s => label(s"usage/$s"))
      }

      object Individual {
        val cpus = sigar.getCpuList.zipWithIndex

        val times = cpus.map {
          case (_, n) => cpuTime(s => CPU.label(s"$n/$s"))
        }

        val usages = cpus.map {
          case (_, n) => cpuUsage(s => CPU.label(s"$n/$s"))
        }
      }
    }
  }
}
