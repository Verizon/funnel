package funnel.integration

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

class IntegrationTarget(val port: Int) extends FlatSpec {
  import scala.concurrent.duration.DurationInt
  import funnel._

  val W = 10.seconds
  val M = Monitoring.instance
  val I = new Instruments(W, M)
  Clocks.instrument(I)
}

class ChemistIntMultiJvmTarget1 extends IntegrationTarget(port = 2001) {
  import funnel.http._
  MonitoringServer.start(M, port)
  Thread.sleep(40000)
}

// this one dies after 20 seconds, so it should be detected and unmonitored
class ChemistIntMultiJvmTarget2 extends IntegrationTarget(port = 2002) {
  import funnel.http._
  MonitoringServer.start(M, port)
  Thread.sleep(20000)
}

class ChemistIntMultiJvmFlask1 extends FlatSpec {
  import funnel._
  import funnel.http._
  import funnel.flask._
  import java.io.File
  import scalaz.concurrent.{Task,Strategy}
  import knobs.{ ClassPathResource, Config, Required }
  import scala.concurrent.duration.DurationInt

  val config: Task[Config] = knobs.loadImmutable(List(Required(ClassPathResource("oncue/flask.cfg"))))

  val options = funnel.Options(Some("flask1"), Some("cluster1"), None, None, 6775, telemetryPort = 7390)

  val I = new funnel.Instruments(1.minute)
  val app = new funnel.flask.Flask(options, I)
  MonitoringServer.start(Monitoring.default, 5775)
  app.run(Array("noretries"))
  Thread.sleep(40000)
  println("flask shutting down")
}

class ChemistIntMultiJvmChemist extends FlatSpec with Matchers with BeforeAndAfterAll {
  import java.net.URI
  import scalaz.concurrent.{Actor,Strategy}
  import scalaz.stream.{Process, async}
  import funnel.chemist._, PlatformEvent._, TargetLifecycle._
  import journal.Logger
  import dispatch._

  val log = Logger[ChemistIntMultiJvmChemist]

  val platform = new IntegrationPlatform {
    val config = new IntegrationConfig
  }

  val core = new IntegrationChemist

  val U1 = new URI("http://localhost:2001/stream/now")
  val U2 = new URI("http://localhost:2002/stream/now")

  override def beforeAll(): Unit = {
    log.info("initializing Chemist")

    Future(Server.unsafeStart(core, platform))(scala.concurrent.ExecutionContext.Implicits.global)

    platform.config.statefulRepository.lifecycle()

    val lifecycleActor: Actor[PlatformEvent] = Actor[PlatformEvent](
      a => platform.config.repository.platformHandler(a).run)(Strategy.Executor(Chemist.serverPool))

    // (repo.repoCommands to Process.constant(Sharding.handleRepoCommand(repo, EvenSharding, networkFlask) _)).run.runAsync(_ => ())

    Thread.sleep(6000)

    lifecycleActor ! NewFlask(Flask(FlaskID("flask1"), Location.localhost, Location.telemetryLocalhost))
    lifecycleActor ! NewTarget(Target("test", U1, false))
    lifecycleActor ! NewTarget(Target("test", U2, false))

    Thread.sleep(40000)
  }

  override def afterAll(): Unit = {
    platform.config.signal.set(false).run
  }

  behavior of "the repository"

  it should "have events in the history" in {
    val history = platform.config.repository.historicalEvents.run
    log.info("history : " + history.toString)
    history.size should be > 0

    val confirmed: Option[Int] = history.collectFirst {
      case RepoEvent.StateChange(_, TargetState.Monitored, Confirmation(_, _, _)) => 1
    }

    confirmed should be (Some(1))
  }

  it should "have gotten the problem event" in {
    val history = platform.config.repository.historicalEvents.run

    val unmonitored: Option[Int] = history.collectFirst {
      case RepoEvent.StateChange(_, TargetState.Problematic, TargetLifecycle.Problem(_, _, _, _)) => 1
    }

    unmonitored should be (Some(1))
  }

  it should "be monitoring 1 but not 2" in {
    val state = platform.config.statefulRepository.stateMaps.get
    log.debug("repository states: " + state)
    log.debug("unmonitored: " + state.lookup(TargetState.Problematic))
    state.lookup(TargetState.Problematic).get.lookup(U2).map(_.msg.target.uri) should be (Some(U2))
    state.lookup(TargetState.Monitored).get.lookup(U1).map(_.msg.target.uri) should be (Some(U1))
  }

  it should "have logged errors" in {
    import funnel._
    val errors = platform.config.repository.errors.run

    log.debug("errors: " + errors)

    errors.size should be > 0

    errors.collectFirst {
      case Error(Names(_, _, u)) => u
    } should be (Some(U2))
  }
}
