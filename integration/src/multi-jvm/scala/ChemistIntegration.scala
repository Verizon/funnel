package funneltest

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}

abstract trait Target {
  def port: Int
  import scala.concurrent.duration.DurationInt
  import funnel._

  val W = 10.seconds
  val M = Monitoring.instance
  val I = new Instruments(W, M)
  Clocks.instrument(I)
}

class ChemistIntMultiJvmTarget1 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2001
  MonitoringServer.start(M, port)
  Thread.sleep(40000)
}

// this one dies after 20 seconds, so it should be detected and unmonitored
class ChemistIntMultiJvmTarget2 extends FlatSpec with Target with Matchers {
  import funnel.http._
  val port = 2002
  MonitoringServer.start(M, port)
  Thread.sleep(20000)
}

class ChemistIntMultiJvmFlask1 extends FlatSpec with Matchers {
  import funnel._
  import funnel.http._
  import funnel.flask._
  import java.io.File
  import scalaz.concurrent.{Task,Strategy}
  import knobs.{ ClassPathResource, Config, Required }
  import scala.concurrent.duration.DurationInt

  val config: Task[Config] = knobs.loadImmutable(List(Required(ClassPathResource("oncue/flask.cfg"))))

  val options = funnel.Options(Some("flask1"), Some("cluster1"), None, None, 6775, telemetryPort = 7391)

  val I = new funnel.Instruments(1.minute)
  val app = new funnel.flask.Flask(options, I)
  MonitoringServer.start(Monitoring.default, 5775)
  app.run(Array("noretries"))
  Thread.sleep(40000)
  println("flask shutting down")
}

class ChemistIntMultiJvmChemist extends FlatSpec with Matchers with BeforeAndAfterAll {
  import scalaz.concurrent.{Actor,Strategy}
  import funnel.chemist._
  import java.net.URI
  import PlatformEvent._
  import scalaz.stream.{Process, async}
  import dispatch._
  import journal.Logger
  import TargetLifecycle._

  val signal = async.signalOf(true)
  val repo = new StatefulRepository

  val log = Logger[ChemistIntMultiJvmChemist]

  val U1 = new URI("http://localhost:2001/stream/now")
  val U2 = new URI("http://localhost:2002/stream/now")

  override def beforeAll(): Unit = {
    println("initializing Chemist")
    val lifecycleActor: Actor[PlatformEvent] = Actor[PlatformEvent](a => repo.platformHandler(a)
.run)(Strategy.Executor(Chemist.serverPool))
    repo.lifecycle()
    val http = Http()
    val networkFlask = new HttpFlask(http, repo, signal)

    (repo.repoCommands to Process.constant(Sharding.handleRepoCommand(repo, EvenSharding, networkFlask) _)).run.runAsync(_ => ())

    Thread.sleep(6000)

    lifecycleActor ! NewFlask(Flask(FlaskID("flask1"), Location.localhost, Location.telemetryLocalhost))
    lifecycleActor ! NewTarget(Target("test", U1, false))
    lifecycleActor ! NewTarget(Target("test", U2, false))

    Thread.sleep(40000)
  }

  "the repository" should "have events in the history" in {
    val history = repo.historicalEvents.run
    log.info("history : " + history.toString)
    history.size should be > 0

    val confirmed: Option[Int] = history.collectFirst {
      case RepoEvent.StateChange(_, TargetState.Monitored, Confirmation(_, _, _)) => 1
    }

    confirmed should be (Some(1))
  }

  "the repository" should "have gotten the umonitor event" in {
    val history = repo.historicalEvents.run

    val unmonitored: Option[Int] = history.collectFirst {
      case RepoEvent.StateChange(_, TargetState.Unmonitored, Unmonitoring(_, _, _)) => 1
    }

    unmonitored should be (Some(1))
  }

  "the repository" should "be monitoring 1 but not 2" in {
    val state = repo.stateMaps.get
    println("repository states: " + state)
    println("unmonitored: " + state.lookup(TargetState.Unmonitored))
    val t2 = repo.targets.get.lookup(U2)
    // perhaps we should have an exception state?
    List(TargetState.Unmonitored, TargetState.Assigned) should contain (t2.get.to)
    state.lookup(TargetState.Monitored).get.lookup(U1).map(_.msg.target.uri) should be (Some(U1))
  }

  override def afterAll(): Unit = {
    signal.set(false).run
  }

  "the repository" should "have logged errors" in {
    import funnel._
    val errors = repo.errors.run

    println("errors: " + errors)

    errors.size should be > 0

    errors.collectFirst {
      case Error(Names(_, _, u)) => u
    } should be (Some(U2))
  }
}
