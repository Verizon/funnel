package funnel

import java.net.URI

import com.twitter.algebird.Group
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

import scala.concurrent.duration._
import scalaz.Nondeterminism
import scalaz.concurrent.{Task, Strategy}
import scalaz.std.list._
import scalaz.std.tuple._
import scalaz.stream.{Process, Sink}
import scalaz.syntax.foldable._
import scalaz.syntax.functor._

object ProcessMirroringEventsSpec extends Properties("processMirroringEvents") {

  private val clusterName: ClusterName = "clusterName"

  property("keys are being removed upon disconnect") = secure {
    val M = Monitoring.default
    val i = new Instruments(monitoring = M)
    val gauge = i.gauge("someGauge",0.0)
    val key = gauge.key

    //closing the signal, simulating the host dropping the connection
    M.get(key).close.run

    val x = M.keySenescence(Events.every(1 milliseconds),Process.eval(Task.delay(gauge.key)))

    Thread.sleep(1000)

    x.run.run
    !M.keys.get.run.contains(key)
  }


  property("TCP disconnect removes host from /mirror/sources list") = secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commands: Process[Task, Command] = Process.emitAll(Seq(mirror, discard))

    //this should be commands.to(enqueueSink) but scala 2.10 didn't like it
    //val commandEnqueue = commands.to(enqueueSink)
    val commandEnqueue = commands.zipWith(enqueueSink)((o,f) => f(o)).eval

    val mockParse: URI => Process[Task, Datapoint[Any]] = 
      _ => Process.eval(Task.delay(throw new RuntimeException("boom")))

    //enqueue the commands
    commandEnqueue.run.run

    //This is used as a flag to cancel the processing
    val b = new java.util.concurrent.atomic.AtomicBoolean(false)

    //start processing commands
    M.processMirroringEvents(
      mockParse,
      nodeRetries = _ => Events.takeEvery(1 millisecond,1)
    ).runAsyncInterruptibly(_ => (), b)

    //Let it run
    Thread.sleep(1000)

    //end processing
    b.set(true)

    //cleanup
    M.mirroringQueue.close.run

    M.mirroringUrls.size == 0
  }

  property("Disconnect command removes host from /mirror/sources list") = secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commands: Process[Task, Command] = Process.emitAll(Seq(mirror, discard))
    val commandEnqueue = commands.zipWith(enqueueSink)((o,f) => f(o)).eval
    val mockParse: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(Task.delay(())
    ){ _ => Task.delay{ () }
    }(_ => Task.delay(datapoint))

    //enqueue the commands
    commandEnqueue.run.run

    //This is used as a flag to cancel the processing
    val b = new java.util.concurrent.atomic.AtomicBoolean(false)

    //start processing commands
    M.processMirroringEvents(mockParse).runAsyncInterruptibly(_ => (), b)

    Thread.sleep(1000)

    //end processing
    b.set(true)
    Thread.sleep(1000)

    //cleanup
    M.mirroringQueue.close.run

    println(M.mirroringUrls.toMap.get(clusterName))
    M.mirroringUrls.toMap.get(clusterName) == None
  }

  property("Disconnect Command disconnects from Host")= secure  {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val result = new java.util.concurrent.atomic.AtomicBoolean(false)
    val countdown = new java.util.concurrent.CountDownLatch(1)
    val commands: Process[Task, Command] = Process.emitAll(Seq(mirror, discard))
    val commandEnqueue = commands.zipWith(enqueueSink)((o,f) => f(o)).eval

    val mockDataConnection: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(Task.delay(())){ _ =>
      Task.delay{
        result.set(true)
        countdown.countDown
        ()
      }

    }(_ => Task.delay(datapoint))

    //enqueue the commands
    commandEnqueue.run.run

    //This is used as a flag to cancel the processing
    val b = new java.util.concurrent.atomic.AtomicBoolean(false)

    //start processing commands
    M.processMirroringEvents(mockDataConnection).runAsyncInterruptibly(_ => (), b)

    Thread.sleep(1000)

    //check whether the io cleanup code was run
    countdown.await(3, java.util.concurrent.TimeUnit.SECONDS)

    //end processing
    b.set(true)

    result.get
  }

 /*
   NOTE WELL
   Currently this failing test kicks off a never-ending process. 
   (The test is that the code stops said process, which fails).
   Continuous running will eventually bog down your system.
   */
  property("Disconnect Command disconnects from Host")= secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val result = new java.util.concurrent.atomic.AtomicBoolean(false)
    val countdown = new java.util.concurrent.CountDownLatch(1)
    val commands1: Process[Task, Command] = Process.emitAll(Seq(mirror))
    val commands2: Process[Task, Command] = Process.emitAll(Seq(discard))

    val command1Enqueue = commands1.zipWith(enqueueSink)((o,f) => f(o)).eval
    val command2Enqueue = commands2.zipWith(enqueueSink)((o,f) => f(o)).eval

    val mockDataConnection: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(
      Task.delay{println("allocate");()}
    ){ _ =>
      Task.delay{
        println("\nCLEANUP\n")
        result.set(true)
        countdown.countDown
        ()
      }

    }(_ => Task.delay(datapoint))

    //This is used as a flag to cancel the processing
    val b = new java.util.concurrent.atomic.AtomicBoolean(false)

    //start processing commands
    M.processMirroringEvents(mockDataConnection).runAsyncInterruptibly(_ => (), b)

    //enqueue the commands
    command1Enqueue.run.run

    Thread.sleep(1000)
    command2Enqueue.run.run

    //check whether the io cleanup code was run
    countdown.await(3, java.util.concurrent.TimeUnit.SECONDS)

    //end processing
    b.set(true)

    result.get
  }

  property("link executes cleanup code on process") = secure {
    implicit val s = Executors.newScheduledThreadPool(4)
    val adp = new AtomicBoolean(false)
    val S = Strategy.Executor(Monitoring.defaultPool)
    val hook = scalaz.stream.async.signalOf[Unit](())(S)
    val other: Process[Task, Unit] = scalaz.stream.io.resource(
      Task.delay{println("allocating");()}
    )(
      _ => Task.delay{adp.set(true);println("cleaning up");()}
    )(_ => Task.delay(()))
    try {
      Task.fork(Monitoring.default.link(hook)(other).run).timed(3.seconds.toMillis).run
    } catch {
      case _:Throwable =>
    }
    Thread.sleep(1000)
    hook.close.run
    Thread.sleep(1000)
    adp.get
  }
}
