package intelmedia.ws.funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._

/** Functions for adding various system metrics collected by SIGAR to a `Monitoring` instance. */
object Sigar {
  type G = Gauge[Periodic[Stats], Double]

  case class CpuStats(user: G,
                      idle: G,
                      total: G,
                      nice: G,
                      irq: G,
                      sys: G,
                      waiting: G,
                      softirq: G,
                      stolen: G)

  case class FileSystemStats(avail: G,
                             usePercent: G,
                             diskQueue: G,
                             free: G,
                             diskReadBytes: G,
                             diskServiceTime: G,
                             diskWrites: G,
                             used: G,
                             diskWriteBytes: G,
                             total: G,
                             files: G,
                             freeFiles: G,
                             diskReads: G)

  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 30 seconds,
             log: String => SafeUnit): SafeUnit = try {
    val sigar = new org.hyperic.sigar.Sigar
    import I._

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
      val actualUsed = mem("actual_used")
      val total = mem("total")
      val usedPercent = pct("used_percent")
      val free = mem("free")
      val actualFree = mem("actual_free")
      val freePercent = pct("free_percent")
      val ram = MB("ram")
    }

    object FileSystem {
      val fileSystems = sigar.getFileSystemList
      val usages = fileSystems.map { d =>
        val dirName = java.net.URLEncoder.encode(d.getDirName, "UTF-8")
        def label(s: String) = s"system/file_system/${dirName}/$s"
        def mem(s: String) = numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero))
        def pct(s: String) = numericGauge(label(s), 0.0, Units.Ratio)
        def num(s: String) = numericGauge(label(s), 0.0, Units.Count)
        def ms(s: String) = numericGauge(label(s), 0.0, Units.Milliseconds)

        (d.getDirName, FileSystemStats(
          avail = mem("avail"),
          usePercent = pct("use_percent"),
          diskQueue = num("disk_queue"),
          free = mem("free"),
          diskReadBytes = mem("disk_read_bytes"),
          diskServiceTime = ms("disk_service_time"),
          diskWrites = num("disk_writes"),
          used = mem("used"),
          diskWriteBytes = mem("disk_write_bytes"),
          total = mem("total"),
          files = num("files"),
          freeFiles = num("free_files"),
          diskReads = num("disk_reads")
        ))
      }.toMap
    }

    object LoadAverage {
      val one = numericGauge("system/load_average/1", 0.0, Units.Count)
      val five = numericGauge("system/load_average/5", 0.0, Units.Count)
      val fifteen = numericGauge("system/load_average/15", 0.0, Units.Count)
    }

    val Uptime = numericGauge("system/uptime", 0.0, Units.Seconds)

    object TCP {
      def label(s: String) = s"system/tcp/$s"
      def num(s: String) = numericGauge(label(s), 0.0, Units.Count)
      val estabResets = num("estab_resets")
      val outSegs = num("outsegs")
      val retransSegs = num("retrans_segs")
      val inErrs = num("in_errs")
      val inSegs = num("in_segs")
      val currEstab = num("curr_estab")
      val passiveOpens = num("passive_opens")
      val activeOpens = num("active_opens")
      val attemptFails = num("attempt_fails")
    }

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

      // Add per-filesystem usages
      FileSystem.usages.map { case (k, v) =>
        try {
          // Getting the mounted file systems so we don't hang
          // if NFS volumes are unreachable
          val u = sigar.getMountedFileSystemUsage(k)
          v.avail.set(u.getAvail)
          v.usePercent.set(u.getUsePercent)
          v.diskQueue.set(u.getDiskQueue)
          v.free.set(u.getFree)
          v.diskReadBytes.set(u.getDiskReadBytes)
          v.diskServiceTime.set(u.getDiskServiceTime)
          v.diskWrites.set(u.getDiskWrites)
          v.used.set(u.getUsed)
          v.diskWriteBytes.set(u.getDiskWriteBytes)
          v.total.set(u.getTotal)
          v.files.set(u.getFiles)
          v.freeFiles.set(u.getFreeFiles)
          v.diskReads.set(u.getDiskReads)
        } catch {
          // Catch exceptions caused by file systems getting dismounted etc.
          case e: Exception => log(e.toString)
        }
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

      // Add uptime
      Uptime.set(sigar.getUptime.getUptime)

      // Add load averages
      val loads = sigar.getLoadAverage
      import LoadAverage._
      List(one, five, fifteen).zip(loads).foreach {
        case (g, d) => g.set(d)
      }

      // Add TCP stats
      val tcp = sigar.getTcp
      TCP.estabResets.set(tcp.getEstabResets)
      TCP.outSegs.set(tcp.getOutSegs)
      TCP.retransSegs.set(tcp.getRetransSegs)
      TCP.inErrs.set(tcp.getInErrs)
      TCP.inSegs.set(tcp.getInSegs)
      TCP.currEstab.set(tcp.getCurrEstab)
      TCP.passiveOpens.set(tcp.getPassiveOpens)
      TCP.activeOpens.set(tcp.getActiveOpens)
      TCP.attemptFails.set(tcp.getAttemptFails)

    }.run.runAsync(_ => ())

  } catch {
    case e: LinkageError =>
      log(e.getMessage)
      log(s"java.library.path is set to: " +
        System.getProperty("java.library.path"))
  }
}
