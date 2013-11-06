package intelmedia.ws.monitoring

import com.twitter.algebird.Group
import org.scalacheck._
import Prop._
import Arbitrary._
import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.Nondeterminism
import scalaz.stream.{process1, Process}

object MonitoringSpec extends Properties("monitoring") {

  val B = Buffers

  /*
   * Check that `roundDuration` works as expected for
   * some hardcoded examples.
   */
  property("roundDuration") = secure {
    B.roundDuration(0 minutes, 5 minutes) == (5 minutes) &&
    B.roundDuration(14 seconds, 1 minutes) == (1 minutes) &&
    B.roundDuration(60 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(61 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(59 seconds, 2 minutes) == (2 minutes) &&
    B.roundDuration(119 seconds, 1 minutes) == (2 minutes) &&
    B.roundDuration(120 seconds, 1 minutes) == (3 minutes) &&
    B.roundDuration(190 milliseconds, 50 milliseconds) == (200 milliseconds)
  }

  /*
   * Check that `counter` properly counts.
   */
  property("counter") = forAll { (xs: List[Int]) =>
    val c = B.counter(0)
    val input: Process[Task,Int] = Process.emitAll(xs)
    val out = input.pipe(c).runLog.run
    out == xs.scanLeft(0)(_ + _)
  }

  /*
   * Check that `resetEvery` properly resets the stream
   * transducer after the elapsed time. Check that `emitEvery`
   * only emits a value at period boundaries.
   */
  property("reset/emitEvery") = forAll { (h: Int, t: List[Int]) =>
    val xs = h :: t
    // resetEvery -- we feed the same input twice, fast forwarding
    // the time; this should give the same output, duplicated
    val c = B.resetEvery(5 minutes)(B.counter(0))
    val input: Process[Task,(Int,Duration)] =
      Process.emitAll(xs.map((_, 0 minutes))) ++
      Process.emitAll(xs.map((_, 5 minutes)))
    val out = input.pipe(c).runLog.run
    require(out.length % 2 == 0, "length of output should be even")
    val (now, later) = out.splitAt(out.length / 2)
    val ok = (now == later) && (now == xs.scanLeft(0)(_ + _))

    // emitEvery -- we should only emit two values, one at the
    // end of the first period, and one at the end of the second
    val c2 = B.emitEvery(5 minutes)(c)
    val input2 = input ++ Process(1 -> (11 minutes))
    val out2 = input2.pipe(c2).runLog.run
    ok && out2.length == 2 && out2(0) == xs.sum && out2(1) == xs.sum
  }

  /* Check that if all events occur at same moment, `sliding` has no effect. */
  property("sliding-id") = forAll(Gen.listOf1(Gen.choose(1,10))) { xs =>
    val c = B.sliding(5 minutes)(identity[Int])(Group.intGroup)
    val input: Process[Task,(Int,Duration)] =
      Process.emitAll(xs.map((_, 1 minutes)))
    val output = input.pipe(c).runLog.run
    output == xs.scanLeft(0)(_ + _)
  }

  /* Example of sliding count. */
  property("sliding-example") = secure {
    val i1: Process[Task, (Int,Duration)] =
      Process(1 -> (0 minutes), 1 -> (1 minutes), 2 -> (3 minutes), 2 -> (4 minutes))

    val c = B.sliding(2 minutes)(identity[Int])(Group.intGroup)
    val output = i1.pipe(c).runLog.run
    output == List(0, 1, 2, 3, 4)
  }

  /*
   * Check that all values are eventually received by a
   * buffered signal.
   */
  property("bufferedSignal") = forAll { (xs: List[Int]) =>
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
    xs.foreach(x => snk(x, _ => ()))
    val expected = xs.sum
    // this will 'eventually' become true, and loop otherwise
    while (s.continuous.once.runLastOr(0).run != expected) {
      Thread.sleep(10)
    }
    true
  }

  /* Check that `distinct` combinator works. */
  property("distinct") = forAll(Gen.listOf1(Gen.choose(-10,10))) { xs =>
    val input: Process[Task,Int] = Process.emitAll(xs)
    input.pipe(B.distinct).runLog.run.toList == xs.distinct
  }

  /* Check that publishing to a bufferedSignal is 'fast'. */
  property("bufferedSignal-profiling") = secure {
    val N = 100000
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
    val t0 = System.nanoTime
    (0 to N).foreach(x => snk(x, _ => ()))
    val expected = (0 to N).sum
    while (s.continuous.once.runLastOr(0).run != expected) {
      Thread.sleep(10)
    }
    val d = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
    // println("Number of microseconds per event: " + d.toMicros)
    // I am seeing around 25 microseconds on avg
    d.toMicros < 500
  }

  /*
   * Counter and Gauge updates should be 'fast', and should work
   * with concurrent producers.
   */
  property("profiling") = secure {
    import instruments._
    val c = counter("uno")
    val ok = gauge("tres", false)
    val N = 1000000
    val t0 = System.nanoTime
    val S = scalaz.concurrent.Strategy.DefaultStrategy
    val f1 = S { (0 until N).foreach { _ =>
      c.increment
      ok.set(true)
    }}
    val f2 = S { (0 until N).foreach { _ =>
      c.increment
      ok.set(true)
    }}
    f1(); f2()
    val updateTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
    val get: Task[Int] = Monitoring.default.latest(c.keys.now)
    while (get.run != N*2) {
      // println("current count: " + get.run)
      Thread.sleep(10)
    }
    val publishTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
    val okResult = Monitoring.default.latest(ok.keys.now).run

    // println("update time: " + updateTime.toNanos)
    // println("publishTime: " + publishTime.toNanos)
    // I am seeing about 40 nanoseconds for update times,
    // 100 nanos for publishing
    updateTime.toNanos < 500 &&
    publishTime.toNanos < 1000 &&
    okResult
  }

  /* Simple sanity check of a timer. */
  property("timer-ex") = secure {
    import instruments._
    val t = timer("uno")
    t.time { Thread.sleep(50) }
    val r = Monitoring.default.latest(t.keys.now).run.mean
    // println("Sleeping for 50ms took: " + r)
    (r - 50).abs < 500
  }

  /* Make sure timer updates are 'fast'. */
  property("timer-profiling") = secure {
    import instruments._
    val t = timer("uno")
    val N = 1000000
    val t0 = System.nanoTime
    val d = (50 milliseconds)
    (0 until N).foreach { _ =>
      t.record(d)
    }
    val delta = System.nanoTime - t0
    val updateTime = (delta nanoseconds) / N.toDouble
    Thread.sleep(100)
    val m = Monitoring.default.latest(t.keys.now).run.mean
    // println("timer:updateTime: " + updateTime)
    updateTime.toNanos < 500 && m == 50
  }

  /* Make sure timers allow concurrent updates. */
  property("concurrent-timing") = secure {
    import instruments._
    val t = timer("uno")
    val N = 100000
    val S = scalaz.concurrent.Strategy.DefaultStrategy
    val t0 = System.nanoTime
    val d1 = (1 milliseconds); val d2 = (3 milliseconds)
    val f1 = S { (0 until N).foreach { _ =>
      t.record(d1)
    }}
    val f2 = S { (0 until N).foreach { _ =>
      t.record(d2)
    }}
    f1(); f2()
    val updateTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
    Thread.sleep(200)
    // average time should be 2 millis
    val m = Monitoring.default.latest(t.keys.now).run.mean
    // println("average time: " + m)
    // println("timer:updateTime: " + updateTime)
    m == 2.0 && updateTime.toNanos < 1000
  }

  property("pub/sub") = forAll(Gen.listOf1(Gen.choose(1,10))) { a =>
    val M = Monitoring.default
    val (k, snk) = M.topic("count")(B.ignoreTime(B.counter(0)))
    val count = M.get(k)
    a.foreach { a => snk(a) }
    val expected = a.sum
    var got = count.continuous.once.map(_.get).runLastOr(0).run
    while (got != expected) {
      got = count.continuous.once.map(_.get).runLastOr(0).run
      Thread.sleep(10)
    }
    true
  }

  /*
   * Feed a counter concurrently from two different threads, making sure
   * the final count is the same as if we summed sequentially.
   */
  property("concurrent-counters-integration-test") = forAll(Gen.listOf1(Gen.choose(-10,10))) { ab =>
    // this test takes about 45 seconds
    val (a,b) = ab.splitAt(ab.length / 2)
    val M = Monitoring.instance
    val I = Instruments.instance(5 minutes, M)
    import I._
    val aN = counter("a")
    val bN = counter("b")
    val abN = counter("ab")
    val latest = Monitoring.snapshot(M)
    Nondeterminism[Task].both(
      Task { a.foreach { a => aN.incrementBy(a); abN.incrementBy(a) } },
      Task { b.foreach { b => bN.incrementBy(b); abN.incrementBy(b) } }
    ).run
    val expectedA = a.sum
    val expectedB = b.sum
    val expectedAB = ab.sum
    @annotation.tailrec
    def go: Unit = {
      val gotA = M.latest(aN.keys.now).run
      val gotB = M.latest(bN.keys.now).run
      val gotAB = M.latest(abN.keys.now).run
      if (gotA != expectedA || gotB != expectedB || gotAB != expectedAB) {
        // println("sleeping")
        // println(s"a: $gotA, b: $gotB, ab: $gotAB")
        Thread.sleep(10)
        go
      }
    }
    go
    val t0 = System.currentTimeMillis
    val m = latest.run // NB: this takes too long
    val millis = System.currentTimeMillis - t0
    // println(s"snapshot took: $millis")
    m(aN.keys.now).get == expectedA &&
    m(bN.keys.now).get == expectedB &&
    m(abN.keys.now).get == expectedAB
  }
}

