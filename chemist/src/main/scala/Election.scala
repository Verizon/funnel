package funnel
package chemist

object Election {

  import scalaz.Monoid
  import scalaz.stream.Process
  import scalaz.concurrent.Task

  trait Ballot[A]
  case class Vote[A](by: A, nominee: A) extends Ballot[A]
  case class Abstain[A](by: A) extends Ballot[A]
  // case object Failed extends Ballot[Nothing]

  trait Decision[A]
  case class Quorum[A](elected: A) extends Decision[A]
  case class Deadlock[A](left: A, right: A) extends Decision[A]


  trait Action[A]
  // i am the leader and should remain leader
  // another peer is leading and alive; i remain passive
  case class Keep[A](peer: A) extends Action[A]
  // 1. another peer may be leading (optional because there may
  // not be a leader at the start of the world) and alive;
  // but i should be leading
  // 2. another peer is leading but not alive
  case class Change[A](from: Option[A], to: A) extends Action[A]


  /*
  Source[A] => Election[A] => Result[A] => Effect[A]
  */

  /* some source of votes; perhaps its real-time, perhapts
     the "votes" are just polling some authoritive system,
     it really doesnt matter provided votes are published */
  type Source[A] = Process[Task, Ballot[A]]

  /* thinking here is that you implement your election in such a
     manner that requires whatever config it needs to determine
     if a quorum has been reached or not */
  type Election[A] = Process[Task, Ballot[A] => Task[Decision[A]]]

  /* given a desicion from an election, properly action that */
  type Result[A] = Process[Task, Decision[A] => Task[Action[A]]]

  /* givn actions of the appropriate type, complete the associated effects */
  type Sink[A] = scalaz.stream.Sink[Task, Action[A]]


  def aggregate[A : Monoid](votes: Process[Task, Seq[Ballot[A]]]): Process[Task, Decision[A]] = {
    import scalaz.std.map._

    votes.flatMap(seq => Process.emitAll(seq)).reduceMap[Map[A,Int]]{
      case Vote(by, nominee) => {
        println("reducing")
        Map(nominee -> 1)
      }
      case Abstain(by) => Map.empty
    }.map(m => Quorum(m.maxBy(_._2)._1))
  }


}

// object Coronator {
//   import Election._
//   import journal.Logger
//   import scalaz.concurrent.Task

//   val log = Logger[Coronator.type]

//   def chemist(d: Discovery, r: Repository, me: Location)(decision: Decision[Location]): Task[Unit] = {
//     Task.delay {
//       decision match {
//         case Quorum(location)     =>
//           if(me == location) Task.now(()) // already the leader, business as usual
//           else

//         case Deadlock(left,right) => // multiple masters detected, stop coordinating
//           log.error("election deadlock encountered; flushing shard distribution to stop futher assignments")
//           for {
//             a <- d.listAllFlasks
//             b  = a.map(f => Task.delay(r.platformHandler(PlatformEvent.TerminatedFlask(f.id))))
//             c <- Task.gatherUnordered(b)
//           } yield c
//       }
//     }
//   }
// }

