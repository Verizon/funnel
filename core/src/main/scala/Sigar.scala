package oncue.svc.funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._

/** Functions for adding various system metrics collected by SIGAR to a `Monitoring` instance. */
class Sigar(I: Instruments, sigar: org.hyperic.sigar.Sigar) {

  type G = Gauge[Periodic[Stats], Double]

  import I._

  case class CpuStats(user: G,
                      idle: G,
                      total: G,
                      sys: G,
                      waiting: G)

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

  object CPU {
    def label(s: String) = s"system/cpu/$s"

    def cpuUsage(label: String => String): CpuStats = {
      def pct(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Ratio, desc)

      CpuStats(pct("user", "Usage of CPU by user code"),
               pct("idle", "CPU idle percentage"),
               pct("combined", "Combined CPU usage"),
               pct("sys", "Usage of CPU by system code"),
               pct("wait", "CPU usage waiting for I/O"))
    }

    object Aggregate {
      def label(s: String) = CPU.label(s"aggregate/$s")

      val usage = cpuUsage(s => label(s"usage/$s"))
    }

  }

  object Mem {
    def label(s: String) = s"system/mem/$s"
    def mem(s: String, desc: String)
      = numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero), desc)
    def MB(s: String, desc: String) =
      numericGauge(label(s), 0.0, Units.Megabytes, desc)
    def pct(s: String, desc: String) =
      numericGauge(label(s), 0.0, Units.Ratio, desc)

    val used = mem("used", "Used system memory including buffers/cache")
    val actualUsed = mem("actual_used", "Used system memory minus buffers/cache")
    val total = mem("total", "Total system memory")
    val usedPercent = pct("used_percent", "Ratio of used to free system memory")
    val free = mem("free", "Free system memory excluding buffers/cached")
    val actualFree = mem("actual_free", "Free system memory plus buffers/cached")
    val freePercent = pct("free_percent", "Ratio of free to used system memory")
    val ram = MB("ram", "Total amount of usable physical memory")
  }

  object FileSystem {
    val fileSystems = sigar.getFileSystemList
    val usages = fileSystems.map { d =>
      val dirName = java.net.URLEncoder.encode(d.getDirName, "UTF-8")
      def label(s: String) = s"system/file_system/${dirName}/$s"
      def mem(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero), desc)
      def pct(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Ratio, desc)
      def num(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Count, desc)
      def ms(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Milliseconds, desc)

      (d.getDirName, FileSystemStats(
        avail = mem("avail", "Free space available to the user"),
        usePercent = pct("use_percent", "Percent of disk used"),
        diskQueue = num("disk_queue", "Length of the disk service queue"),
        free = mem("free", "Total free space"),
        diskReadBytes = mem("disk_read_bytes", "Physical disk bytes read"),
        diskServiceTime = ms("disk_service_time", "Time taken to service a read/write request"),
        diskWrites = num("disk_writes", "Physical disk writes"),
        used = mem("used", "Total used space on filesystem"),
        diskWriteBytes = mem("disk_write_bytes", "Physical disk bytes written"),
        total = mem("total", "Total size of filesystem"),
        files = num("files", "Number of nodes on filesystem"),
        freeFiles = num("free_files", "Number of free nodes on filesystem"),
        diskReads = num("disk_reads", "Physical disk reads")
      ))
    }.toMap
  }

  object LoadAverage {
    val one = numericGauge("system/load_average/1", 0.0, Units.Count,
      "1-minute load average")
    val five = numericGauge("system/load_average/5", 0.0, Units.Count,
      "5-minute load average")
    val fifteen = numericGauge("system/load_average/15", 0.0, Units.Count,
      "15-minute load average")
  }

  val Uptime = numericGauge("system/uptime", 0.0, Units.Seconds, "System uptime")

  object TCP {
    def label(s: String) = s"system/tcp/$s"
    def num(s: String, desc: String) = numericGauge(label(s), 0.0, Units.Count, desc)
    val estabResets =
      num("estab_resets",
          "Number of connections reset by peer rather than gracefully finalized")
    val outSegs = num("outsegs", "Number of segments sent")
    val retransSegs = num("retrans_segs", "Number of segments retransmitted")
    val inErrs = num("in_errs", "Number of received segments with bad checksums")
    val inSegs = num("in_segs", "Number of segments received")
    val currEstab = num("curr_estab", "Number of open sockets")
    val passiveOpens = num("passive_opens", "Number of open server connections")
    val activeOpens = num("active_opens", "Number of open client connections")
    val attemptFails = num("attempt_fails", "Number of failed client connections")
  }

  def instrument(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 30 seconds,
             log: String => Unit): Unit = {

    // Make the side effects happen.
    // Side effects FTL!
    val _ = (TCP, LoadAverage, FileSystem, CPU.Aggregate, Mem)

    Process.awakeEvery(t)(Strategy.Executor(ES), TS).map { _ =>
      import org.hyperic.sigar.{Cpu, CpuPerc}

      def cpuUsage(cpu: CpuPerc, m: CpuStats) = {
        m.user.set(cpu.getUser)
        m.idle.set(cpu.getIdle)
        m.total.set(cpu.getCombined)
        m.sys.set(cpu.getSys)
        m.waiting.set(cpu.getWait)
      }

      // Add aggregate CPU usage
      cpuUsage(sigar.getCpuPerc, CPU.Aggregate.usage)

      // Add per-filesystem usages
      FileSystem.usages.foreach { case (k, v) =>
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
  }
}

object Sigar {
  def apply(I: Instruments)(implicit log: String => Unit): Option[Sigar] = {
    try {
      val sigar = new org.hyperic.sigar.Sigar
      // internals of sigar seem to be lazy, so lets force a
      // LinkageError if there is going to be one.
      // Did I mention that I *hate* native libraries....
      sigar.getPid

      Option(new Sigar(I, sigar))
    } catch {
      case e: LinkageError =>
        log("Unable to load native Sigar library, it may not be installed?")
        log(s"The following error was encountered: $e")
        log("java.library.path is set to: " +
          System.getProperty("java.library.path"))
        None
    }
  }

  def instrument(I: Instruments)(implicit log: String => Unit): Unit =
    apply(I).foreach(_.instrument)

}
