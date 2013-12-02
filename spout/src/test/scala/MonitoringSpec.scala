package intelmedia.ws
package monitoring

import com.twitter.algebird.Group
import org.scalacheck._
import Prop._
import Arbitrary._
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.Nondeterminism
import scalaz.stream.{process1, Process}

object MonitoringSpec extends Properties("monitoring") {

  val B = Buffers

  // we are dealing with doubles, so don't want to rely on exact comparison,
  // should be within a small epsilon though
  val Epsilon = 1e-7

  // some syntax for === and !== for epsion comparisons
  implicit class DoubleSyntax(d: Double) {
    def ===[N](n: N)(implicit N: Numeric[N]) = (d - N.toDouble(n)).abs < Epsilon
    def !==[N](n: N)(implicit N: Numeric[N]) = (d - N.toDouble(n)).abs >= Epsilon
  }
  implicit class DoubleSeqSyntax(ds: Seq[Double]) {
    def ===[N](ns: Seq[N])(implicit N: Numeric[N]) =
      ns.length == ds.length && ds.zip(ns.map(N.toDouble)).forall { case (a,b) => a === b }
  }

  // +/- 200 trillion
  implicit val arbLong: Arbitrary[Long] = Arbitrary(Gen.choose(-200000000000000L, 200000000000000L))

  /*
   * Check that `roundDuration` works as expected for
   * some hardcoded examples.
   */
  property("roundDuration") = secure {
    B.ceilingDuration(0 minutes, 5 minutes) == (5 minutes) &&
    B.ceilingDuration(14 seconds, 1 minutes) == (1 minutes) &&
    B.ceilingDuration(60 seconds, 1 minutes) == (2 minutes) &&
    B.ceilingDuration(61 seconds, 1 minutes) == (2 minutes) &&
    B.ceilingDuration(59 seconds, 2 minutes) == (2 minutes) &&
    B.ceilingDuration(119 seconds, 1 minutes) == (2 minutes) &&
    B.ceilingDuration(120 seconds, 1 minutes) == (3 minutes) &&
    B.ceilingDuration(190 milliseconds, 50 milliseconds) == (200 milliseconds)
  }

  /*
   * Check that `counter` properly counts.
   */
  property("counter") = forAll { (xs: List[Long]) =>
    val c = B.counter(0)
    val input: Process[Task,Long] = Process.emitAll(xs)
    val out = input.pipe(c).runLog.run
    out == xs.scanLeft(0.0)(_ + _)
  }

  /*
   * Check that `resetEvery` properly resets the stream
   * transducer after the elapsed time. Check that `emitEvery`
   * only emits a value at period boundaries.
   */
  property("reset/emitEvery") = forAll { (h: Long, t: List[Long]) =>
    val xs = h :: t
    // resetEvery -- we feed the same input twice, fast forwarding
    // the time; this should give the same output, duplicated
    val c = B.resetEvery(5 minutes)(B.counter(0))
    val input: Process[Task,(Long,Duration)] =
      Process.emitAll(xs.map((_, 0 minutes))) ++
      Process.emitAll(xs.map((_, 5 minutes)))
    val out = input.pipe(c).runLog.run
    require(out.length % 2 == 0, "length of output should be even")
    val (now, later) = out.splitAt(out.length / 2)
    val ok = (now === later) && (now === xs.scanLeft(0.0)(_ + _))

    // emitEvery -- we should only emit two values, one at the
    // end of the first period, and one at the end of the second
    val c2 = B.emitEvery(5 minutes)(c)
    val input2 = input ++ Process(1L -> (11 minutes))
    val out2 = input2.pipe(c2).runLog.run
    ok && out2.length === 2 && out2(0) === xs.sum && out2(1) === xs.sum
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
  property("bufferedSignal") = forAll { (xs: List[Long]) =>
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
    xs.foreach(snk)
    val expected = xs.sum
    // this will 'eventually' become true, and loop otherwise
    while (s.continuous.once.runLastOr(0.0).run !== expected) {
      Thread.sleep(10)
    }
    true
  }

  /* Check that `distinct` combinator works. */
  property("distinct") = forAll(Gen.listOf1(Gen.choose(-10L,10L))) { xs =>
    val input: Process[Task,Long] = Process.emitAll(xs)
    input.pipe(B.distinct).runLog.run.toList == xs.distinct
  }

  /* Check that publishing to a bufferedSignal is 'fast'. */
  property("bufferedSignal-profiling") = secure {
    def go: Boolean = {
      val N = 100000
      val (snk, s) = Monitoring.bufferedSignal(B.counter(0))
      val t0 = System.nanoTime
      (0 to N).foreach(x => snk(x))
      val expected = (0 to N).map(_.toDouble).sum
      while (s.continuous.once.runLastOr(0.0).run !== expected) {
        Thread.sleep(10)
      }
      val d = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      // println("Number of microseconds per event: " + d.toMicros)
      // I am seeing around 25 microseconds on avg
      d.toMicros < 1000
    }
    go || go || go // decrease false negative rate by retrying three times
  }

  /*
   * Counter and Gauge updates should be 'fast', and should work
   * with concurrent producers.
   */
  property("profiling") = secure {
    def go: Boolean = {
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
      val get: Task[Double] = Monitoring.default.latest(c.keys.now)
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
      updateTime.toNanos < 1000 &&
      publishTime.toNanos < 2000 &&
      okResult
    }
    go || go || go
  }

  /* Simple sanity check of a timer. */
  property("timer-ex") = secure {
    def go: Boolean = {
      import instruments._
      val t = timer("uno")
      t.time { Thread.sleep(50) }
      val r = Monitoring.default.latest(t.keys.now).run.mean
      // println("Sleeping for 50ms took: " + r)
      (r - 50).abs < 1000
    }
    go || go || go
  }

  /* Make sure timer updates are 'fast'. */
  property("timer-profiling") = secure {
    def go: Boolean = {
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
      updateTime.toNanos < 1000 && m == 50
    }
    go || go || go
  }

  /* Make sure timers allow concurrent updates. */
  property("concurrent-timing") = secure {
    def go: Boolean = {
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
      m === 2.0 && updateTime.toNanos < 1000
    }
    go || go || go
  }

  /** Check that when publishing, we get the count that was published. */
  property("pub/sub") = forAll(Gen.listOf1(Gen.choose(1,10))) { a =>
    val M = Monitoring.default
    val (k, snk) = M.topic[Long,Double]("count", Units.Count)(B.ignoreTime(B.counter(0)))
    val count = M.get(k)
    a.foreach { a => snk(a) }
    val expected = a.sum
    var got = count.continuous.once.runLastOr(0.0).run
    while (got !== expected) {
      got = count.continuous.once.runLastOr(0.0).run
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
    val I = new Instruments(5 minutes, M)
    import I._
    val aN = counter("a")
    val bN = counter("b")
    val abN = counter("ab")
    val latest = Monitoring.snapshot(M)
    Nondeterminism[Task].both(
      Task { a.foreach { a => aN.incrementBy(a); abN.incrementBy(a) } },
      Task { b.foreach { b => bN.incrementBy(b); abN.incrementBy(b) } }
    ).run
    val expectedA: Double = a.map(_.toDouble).sum
    val expectedB: Double = b.map(_.toDouble).sum
    val expectedAB: Double = ab.map(_.toDouble).sum
    @annotation.tailrec
    def go: Unit = {
      val gotA: Double = M.latest(aN.keys.now).run
      val gotB: Double = M.latest(bN.keys.now).run
      val gotAB: Double = M.latest(abN.keys.now).run
      if ((gotA !== expectedA) || (gotB !== expectedB) || (gotAB !== expectedAB)) {
        // println("sleeping")
        // println(s"a: $gotA, b: $gotB, ab: $gotAB")
        Thread.sleep(10)
        go
      }
    }
    go
    val t0 = System.currentTimeMillis
    val m = latest.run
    val millis = System.currentTimeMillis - t0
    // println(s"snapshot took: $millis")
    (m(aN.keys.now).value.asInstanceOf[Double] === expectedA) &&
    (m(bN.keys.now).value.asInstanceOf[Double] === expectedB) &&
    (m(abN.keys.now).value.asInstanceOf[Double] === expectedAB)
  }

  property("derived-metrics") = forAll(Gen.listOf1(Gen.choose(-10,10))) { ls0 =>
    val ls = ls0.take(50)
    implicit val M = Monitoring.instance
    val I = new Instruments(5 minutes, M); import I._
    val a = counter("a")
    val b = counter("b")

    val ab = Metric.apply2(a.key, b.key)(_ + _)
    val kab1 = ab.publishEvery(30 milliseconds)("sum:ab-1", Units.Count)
    val kab2 = ab.publishOnChange(a.key)("sum:ab-2", Units.Count)
    val kab3 = ab.publishOnChanges(a.key, b.key)("sum:ab-3", Units.Count)

    Strategy.Executor(Monitoring.defaultPool) {
      ls.foreach(a.incrementBy)
    }
    Strategy.Executor(Monitoring.defaultPool) {
      ls.foreach(b.incrementBy)
    }

    val expected = ls.map(_.toDouble).sum * 2

    def go(rounds: Int): Prop = {
      Thread.sleep(30)
      val ab1r = M.latest(kab1).run
      val ab2r = M.latest(kab2).run
      val ab3r = M.latest(kab3).run
      // since ab2r is only refreshed when `a` changes, we
      // artifically refresh `a`, otherwise this test would
      // have a race condition if `a` completed before `b`
      if (ab2r != expected) a.incrementBy(0)
      // println((ab1r, ab2r, ab3r))
      (ab1r === ab2r) && (ab2r === ab3r) && (ab3r === expected) || {
        if (rounds == 0) "results: " + (ab1r, ab2r, ab3r).toString |: false
        else go(rounds - 1)
      }
    }
    go(15)
  }

  val bools = for {
    n <- Gen.choose(0,25000)
    bs <- Gen.listOfN(n, arbitrary[Boolean])
  } yield bs

  property("Metric.bsequence") = forAll(bools) { bs =>
    import scalaz.~>
    import scalaz.std.option._
    val alwaysNone = new (Key ~> Option) { def apply[A](k: Key[A]) = None }
    val expected = Policies.majority(bs)
    Metric.bsequence(bs.map(Metric.point(_)))
          .map(Policies.majority)
          .run(alwaysNone)
          .get == expected
  }

  property("aggregate") = secure {
    List(List(), List(1), List(-1,1), List.range(0,100)).forall { xs =>
      val M = Monitoring.instance
      val I = new Instruments(5 minutes, M)
      val counters = xs.zipWithIndex.map { case (x,i) =>
        val c = I.counter(s"count/$i")
        c.incrementBy(x)
        c
      }
      val family = Key[Double]("now/count", Units.Count)
      val out = Key[Double]("sum", Units.Count)
      M.aggregate(family, out)(Events.takeEvery(15 milliseconds, 50))(_.sum).run
      Thread.sleep(1000)
      // println("xs: " + xs)
      val l = M.latest(out).run
      val r = xs.map(_.toDouble).sum
      l === r || { println(l, r); false }
    }
  }

  import argonaut.{DecodeJson, EncodeJson, Parse}

  def roundTrip[A:EncodeJson:DecodeJson](a: A): Prop = {
    val out1: String = implicitly[EncodeJson[A]].apply(a).nospaces
    // println(out1)
    val parsed = Parse.decode[A](out1).toOption.get
    val out2: String = implicitly[EncodeJson[A]].apply(parsed).nospaces
    out1 == out2 || (s"out1: $out1, out2: $out2" |: false)
  }

  property("NaN handling") = secure {
    import JSON._
    roundTrip(Stats.statsGroup.zero) &&
    roundTrip(Double.NaN) &&
    roundTrip(Double.PositiveInfinity) &&
    roundTrip(Double.NegativeInfinity)
  }
}

