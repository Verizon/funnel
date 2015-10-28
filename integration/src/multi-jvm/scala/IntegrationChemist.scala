package funnel
package integration

import scalaz.Scalaz
import scala.concurrent.duration._
import scalaz.stream.async.boundedQueue
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process,async,time}
import chemist.{Chemist,PlatformEvent,Pipeline,sinks}

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._, PlatformEvent._, Pipeline.contextualise
  import Chemist.Flow

  private[this] val log = journal.Logger[IntegrationChemist]

  val lifecycle: Flow[PlatformEvent] =
    Process.emitAll(NoOp :: Nil).map(contextualise)

  val queue =
    boundedQueue[PlatformEvent](100)(Chemist.defaultExecutor)

  def synthesize(e: PlatformEvent): Unit =
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>> " + queue.enqueueOne(e).attempt.run)

  val init: ChemistK[Unit] =
    for {
      cfg <- config
      _    = log.info("Initilizing Chemist....")
      _   <- queue.enqueueOne(TerminatedTarget(new java.net.URI("http://bbc.co.uk/"))).liftKleisli
      _   <- Pipeline.task(
            lifecycle,
            cfg.rediscoveryInterval
          )(cfg.discovery,
            queue,
            cfg.sharder,
            cfg.http,
            cfg.state,
            sinks.unsafeNetworkIO(cfg.remoteFlask, queue)
          ).liftKleisli
    } yield ()
}
