
package funnel

import java.net.URI

import com.twitter.algebird.Group
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._

import scala.concurrent.duration._
import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.std.tuple._
import scalaz.stream.{Process, Sink}
import scalaz.syntax.foldable._
import scalaz.syntax.functor._

object ThrowawaySpec extends Properties("monitoring") {

  /*
  property("Disconnect Command disconnects from Host")= secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue

    val uri= URI.create("http://localhost")

    val mirror = Mirror(uri,"clustername")

    val discard = Discard(uri)

    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")

    val result = new java.util.concurrent.atomic.AtomicBoolean(false)

    val countdown = new java.util.concurrent.CountDownLatch(1)

    val commandEnqueue = Process.emitAll(Seq(mirror, discard)).to(enqueueSink)

    val mockParse: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(Task.delay(())){ _ => 
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
    M.processMirroringEvents(mockParse).runAsyncInterruptibly(_ => (), b)

    Thread.sleep(1000)

    //end processing
    b.set(true)

    //cleanup
    M.mirroringQueue.close.run

    //check whether the io cleanup code was run
    countdown.await(3, java.util.concurrent.TimeUnit.SECONDS)

    result.get
  }
  property("Disconnect command removes host from /mirror/sources list") = secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,"clustername")
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commandEnqueue = Process.emitAll(Seq(mirror, discard)).to(enqueueSink)
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

    println("disconnect command /mirror sources" + M.mirroringUrls.size)
    M.mirroringUrls.size == 0
  }
  */
  property("TCP disconnect removes host from /mirror/sources list") = secure {
    val M = Monitoring.default
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,"clustername")
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commandEnqueue = Process.emitAll(Seq(mirror, discard)).to(enqueueSink)
    val mockParse: URI => Process[Task, Datapoint[Any]] = _ => Process.eval(Task.delay(throw new RuntimeException("boom")))

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

    println("tcp disconnect /mirror sources" + M.mirroringUrls.size)
    M.mirroringUrls.size == 0
  }

}
