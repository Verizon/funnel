package funnel
package chemist

import java.net.InetAddress
import scalaz.concurrent.Task
import scalaz.stream.{Process,channel,sink}
import org.scalatest.{FlatSpec,Matchers}

class ElectionSpec extends FlatSpec with Matchers {
  import Election._

  val peer1 = InetAddress.getLocalHost.getAddress.mkString(".")
  val peer2 = "10.100.1.1"
  val peer3 = "10.100.1.2"

  val votes: Election.Source[String] =
    Process.emitAll(
      Vote(by = peer1, nominee = peer2) ::
      Vote(by = peer2, nominee = peer1) ::
      Vote(by = peer3, nominee = peer1) :: Nil)

  // val election: Election.Election[String] = {
  //   import collection.mutable.{Map => MutableMap}
  //   var state: MutableMap[String, Int] = MutableMap.empty
  //   def leader: Decision[String] = Quorum(state.maxBy(_._2)._1)
  //   channel.lift {
  //     case Vote(by,nominee) => Task.delay {
  //       state.update(nominee, state.get(nominee).map(_+1).getOrElse(1))
  //       leader
  //     }
  //     case _                => Task.now(leader)
  //   }
  // }

  // based on the incoming desicion, one needs to figure out
  // if we are already the leader, or if someone else is. we
  // do this so that we can figure out what effects would need
  // to take place.
  val result: Election.Result[String] =
    channel.lift {
      case Quorum(peer) => Task.delay {
        println(">>>>>>>>>>>")

        val me = InetAddress.getLocalHost.getAddress.mkString(".")
        if(me == peer) Keep(me)
        else Change(from = Some(peer), to = me)
      }
      case Deadlock(left,right) => ???
    }

  def effects: Sink[String] =
    sink.lift {
      case Keep(peer)       => Task.now(println(s"keeping $peer"))
      case Change(from, to) => Task.now(println(s"changing from $from to $to"))
    }

  it should "foo" in {
    import scalaz.std.string._

    // println {
    //   Election.aggregate(votes).through(result).to(effects).runLog.run
    // }
    import scala.concurrent.duration._

    val votes2 =
      Events.every(2.seconds)(Monitoring.schedulingPool)(Monitoring.default
        ).map { _ =>
          println("voting...")
          Vote(by = peer1, nominee = peer2)
        }

    println {
      votes2.map(v => Map[String,Int](v.nominee -> 1))
        .scanMonoid
        .map(i => {println(i); i})
        .runLog.run
    }

    // Election.aggregate(votes2.chunk(3)).through(result).to(effects).runLog.run

  }

}
