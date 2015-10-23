package funnel
package integration

import chemist.{Chemist,PlatformEvent,Pipeline,sinks}
import scalaz.Scalaz
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.async

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._, PlatformEvent._, Pipeline.contextualise

  private[this] val log = journal.Logger[IntegrationChemist]

  private[this] val lifecycle =
    async.signalOf[PlatformEvent](NoOp)(Strategy.Executor(Chemist.serverPool))

  def init: ChemistK[Unit] =
    for {
      cfg <- config
      _    = log.info("Initilizing Chemist....")
      _   <- Pipeline.task(
            lifecycle.discrete.map(contextualise),
            cfg.rediscoveryInterval
          )(cfg.discovery,
            cfg.sharder,
            cfg.http,
            sinks.caching(cfg.state),
            sinks.unsafeNetworkIO(cfg.remoteFlask)
          ).liftKleisli
    } yield ()
}
