package funnel
package integration

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.duration._
import journal.Logger

class ChemistIntMultiJvmTarget1 extends IntegrationTarget(
  port = 2001, delay = 40.seconds)

// this one dies after 20 seconds, so it should be detected and unmonitored
class ChemistIntMultiJvmTarget2 extends IntegrationTarget(
  port = 2002, delay = 20.seconds)

class ChemistIntMultiJvmFlask1 extends FlatSpec {
  import funnel.http._
  import funnel.flask._
  import java.io.File
  import scalaz.concurrent.{Task,Strategy}

  val log = Logger[this.type]

  val options = IntegrationFixtures.flask1Options

  val I = new funnel.Instruments(1.minute)
  val app = new funnel.flask.Flask(options, I)

  app.run(Array("noretries"))
  Thread.sleep(40000)
  log.debug("flask shutting down")
}

class ChemistIntMultiJvmChemist extends FlatSpec with Matchers with BeforeAndAfterAll {
  import java.net.URI
  import scalaz.Kleisli
  import scalaz.concurrent.{Actor,Strategy,Task}
  import scalaz.stream.{Process, async}
  import funnel.chemist._, PlatformEvent._, TargetLifecycle._
  import dispatch._
  import IntegrationFixtures._

  val log = Logger[ChemistIntMultiJvmChemist]

  val platform = new IntegrationPlatform {
    val config = new IntegrationConfig
  }

  val ichemist = new IntegrationChemist

  val U1 = new URI("http://localhost:2001/stream/now")
  val U2 = new URI("http://localhost:2002/stream/now")

  implicit class KleisliExeSyntax[A](k: Kleisli[Task,IntegrationPlatform,A]){
    def exe: A = k.apply(platform).run
  }

  override def beforeAll(): Unit = {
    log.info("initializing Chemist")

    // just fucking fork the shit out of it.
    Future(Server.unsafeStart(ichemist, platform))(scala.concurrent.ExecutionContext.Implicits.global)

    val lifecycleActor: Actor[PlatformEvent] = Actor[PlatformEvent](
      a => platform.config.repository.platformHandler(a).run)(Strategy.Executor(Chemist.serverPool))

    Thread.sleep(6000)

    lifecycleActor ! NewFlask(Flask(FlaskID("flask1"), flask1.location, flask1.telemetry))
    lifecycleActor ! NewTarget(Target("test", U1, false))
    lifecycleActor ! NewTarget(Target("test", U2, false))

    Thread.sleep(40000)
  }

  override def afterAll(): Unit = {
    platform.config.signal.set(false).run
  }

  behavior of "repository"

  it should "have events in the history" in {
    val history = platform.config.repository.historicalRepoEvents.run
    log.info("history : " + history.toString)
    log.info("phistory : " + platform.config.repository.historicalPlatformEvents.run.toString)
    history.size should be > 0

    val confirmed: Option[Int] = history.collectFirst {
      case RepoEvent.StateChange(_, TargetState.Monitored, Confirmation(_, _, _)) => 1
    }

    confirmed should be (Some(1))
  }

  it should "have gotten the problem event" in {
    val history = platform.config.repository.historicalRepoEvents.run

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

  behavior of "chemist"

  it should "list the appropriate flask ids" in {
    ichemist.shards.exe should equal ( Set(FlaskID("flask1")) )
  }

  it should "show more detailed flask information" in {
    ichemist.shard(FlaskID("flask1")).exe should equal ( Some(flask1) )
  }

  it should "show the correct distribution" in {
    println("===========================")
    // println(ichemist.states.exe)
    println(ichemist.distribution.exe)
    // println(platform.config.statefulRepository.stateMaps)
    println("===========================")
  }

}
