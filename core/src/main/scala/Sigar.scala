package intelmedia.ws.funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._

/** Functions for adding various system metrics collected by SIGAR to a `Monitoring` instance. */
object Sigar {
  type G = Gauge[Periodic[Stats],Double]

  case class CpuStats(user: G,
                      idle: G,
                      total: G,
                      nice: G,
                      irq: G,
                      sys: G,
                      waiting: G,
                      softirq: G,
                      stolen: G)

  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 3 seconds): Unit = {
    val sigar = new org.hyperic.sigar.Sigar
    import I._

    Process.awakeEvery(t)(ES, TS).map { _ =>
      import org.hyperic.sigar.{Cpu, CpuPerc}

      def cpuTime(cpu: Cpu, m: CpuStats): SafeUnit = {
        m.user.set(cpu.getUser)
        m.idle.set(cpu.getIdle)
        m.total.set(cpu.getTotal)
        m.nice.set(cpu.getNice)
        m.irq.set(cpu.getIrq)
        m.sys.set(cpu.getSys)
        m.waiting.set(cpu.getWait)
        m.softirq.set(cpu.getSoftIrq)
        m.stolen.set(cpu.getStolen)
      }
      def cpuUsage(cpu: CpuPerc, m: CpuStats) = {
        m.user.set(cpu.getUser)
        m.idle.set(cpu.getIdle)
        m.total.set(cpu.getCombined)
        m.nice.set(cpu.getNice)
        m.irq.set(cpu.getIrq)
        m.sys.set(cpu.getSys)
        m.waiting.set(cpu.getWait)
        m.softirq.set(cpu.getSoftIrq)
        m.stolen.set(cpu.getStolen)
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

      // Add memory stats
      val mem = sigar.getMem
      Mem.used.set(mem.getUsed)
      Mem.actualUsed.set(mem.getActualUsed)
      Mem.total.set(mem.getTotal)
      Mem.usedPercent.set(mem.getUsedPercent)
      Mem.free.set(mem.getFree)
      Mem.actualFree.set(mem.getActualFree)
      Mem.freePercent.set(mem.getFreePercent)
      Mem.ram.set(mem.getRam)

    }.run.runAsync(_ => ())

    object CPU {
      def label(s: String) = s"system/cpu/$s"

      def cpuTime(label: String => String): CpuStats = {
        def ms(s: String) = numericGauge(label(s), 0.0, Units.Milliseconds)

        CpuStats(ms("user"),
                 ms("idle"),
                 ms("total"),
                 ms("nice"),
                 ms("irq"),
                 ms("sys"),
                 ms("wait"),
                 ms("softirq"),
                 ms("stolen"))
      }

      def cpuUsage(label: String => String): CpuStats = {
        def pct(s: String) = numericGauge(label(s), 0.0, Units.Ratio)

        CpuStats(pct("user"),
                 pct("idle"),
                 pct("combined"),
                 pct("nice"),
                 pct("irq"),
                 pct("sys"),
                 pct("wait"),
                 pct("softirq"),
                 pct("stolen"))
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

    object Mem {
      def label(s: String) = s"system/mem/$s"
      def mem(s: String) = numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero))
      def MB(s: String) = numericGauge(label(s), 0.0, Units.Megabytes)
      def pct(s: String) = numericGauge(label(s), 0.0, Units.Ratio)

      val used = mem("used")
      val actualUsed = mem("actualUsed")
      val total = mem("total")
      val usedPercent = pct("usedPercent")
      val free = mem("free")
      val actualFree = mem("actualFree")
      val freePercent = pct("freePercent")
      val ram = MB("ram")
    }
  }
}
