package funnel
package integration

import org.scalatest.{BeforeAndAfterAll,FlatSpecLike,Matchers}
import akka.remote.testkit.MultiNodeSpecCallbacks

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()
  override def afterAll() = multiNodeSpecAfterAll()
}

//////////////////////// JVM configuration //////////////////////////

import akka.remote.testkit.MultiNodeConfig
import java.net.URI
import scalaz.Kleisli
import scalaz.concurrent.Task

object MultiNodeSampleConfig extends MultiNodeConfig {
  /**
   * important thing to note here is that each role needs to be assigned
   * to a specific jvm. If its not, the test will complain there is not
   * enough nodes to run the test suite.
   */
  val chemist01 = role("chemist01")
  val flask01   = role("flask01")
  val target01  = role("target01")
  val target02  = role("target02")
  val target03  = role("target03")

  //////// barriers /////////
  val Startup      = "startup"
  val Deployed     = "deployed"
  val PhaseOne     = "phase-one"
  val PhaseTwo     = "phase-two"
  val Finished     = "finished"

  val platform = new IntegrationPlatform {
    val config = new IntegrationConfig
  }

  val ichemist = new IntegrationChemist

  val http = dispatch.Http()

  implicit class KleisliExeSyntax[A](k: Kleisli[Task,IntegrationPlatform,A]){
    def exe: A = k.apply(platform).run
  }
}

//////////////////////// JVM setup //////////////////////////

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.actor.{Props,Actor}

class MultiNodeSampleSpecMultiJvmNode1 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode2 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode3 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode4 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode5 extends MultiNodeSample

//////////////////////// Actual Test //////////////////////////

import akka.remote.testconductor.{RoleName,Controller}
import concurrent.{Future,ExecutionContext}
import chemist.{Server,FlaskID,PlatformEvent,TargetLifecycle,RepoEvent}
import java.util.concurrent.{CountDownLatch,TimeUnit}
import concurrent.duration._

class MultiNodeSample extends MultiNodeSpec(MultiNodeSampleConfig)
  with STMultiNodeSpec
  with ImplicitSender {
  import MultiNodeSampleConfig._
  import PlatformEvent._, TargetLifecycle._

  def fetch(path: String) = {
    import dispatch._, Defaults._

    val svc = url(s"http://127.0.0.1:64529${path}")
    val json = http(svc OK as.String)
    println("-----------------------------")
    println(scala.concurrent.Await.result(json, 1.seconds))
    println("-----------------------------")
  }

  def deployTarget(role: RoleName, port: Int, failAfter: Option[Duration] = None) =
    runOn(role){
      val latch = new CountDownLatch(1)
      val target = IntegrationTarget.start(port)
      enterBarrier(Deployed, PhaseOne)
      failAfter.foreach { d =>
        latch.await(d.toMillis, TimeUnit.MILLISECONDS)
        target.stop()
        Thread.sleep(15.seconds.toMillis)
      }
      enterBarrier(PhaseTwo)
    }

  def deployFlask(role: RoleName, opts: flask.Options) =
    runOn(role){
      IntegrationFlask.start(opts)
      enterBarrier(Deployed, PhaseOne, PhaseTwo)
    }

  def initialParticipants =
    roles.size

  it should "wait for all nodes to enter startup barrier" in {
    enterBarrier(Startup)
  }

  it should "setup the world" in {
    runOn(chemist01){
      enterBarrier(Deployed)
      // just fork the shit out of it so it doesnt block our test.
      Future(Server.unsafeStart(ichemist, platform)
        )(ExecutionContext.Implicits.global)

      Thread.sleep(5.seconds.toMillis) // give the system time to do its thing

      enterBarrier(PhaseOne)
    }

    deployTarget(target01, 4001)

    deployTarget(target02, 4002)

    deployTarget(target03, 4003, Some(5.seconds))

    deployFlask(flask01, IntegrationFixtures.flask1Options)

  }

  it should "list the appropriate flask ids" in {
    runOn(chemist01){
      ichemist.shard(FlaskID("flask1")).exe should equal (
        Some(IntegrationFixtures.flask1) )
    }
  }

  it should "show more detailed flask information" in {
    runOn(chemist01){
      ichemist.shard(FlaskID("flask1")).exe should equal (
        Some(IntegrationFixtures.flask1) )
    }
  }

  it should "show the correct distribution" in {
    runOn(chemist01){
      ichemist.distribution.exe.toList.sortBy(_._1.value).toMap should equal (
        Map(FlaskID("flask1") -> Map(
          "target01" -> List(new URI("http://localhost:4001/stream/now")),
          "target03" -> List(new URI("http://localhost:4003/stream/now")),
          "target02" -> List(new URI("http://localhost:4002/stream/now"))))
      )
    }
  }

  it should "have events in the history" in {
    runOn(chemist01){
      val history = ichemist.platformHistory.exe
      history.size should be > 0
    }
  }

  it should "have recieved the problem event via the telemetry socket" in {
    import org.scalatest.OptionValues._
    runOn(chemist01){
      enterBarrier(PhaseTwo)
      println(">>>><<<<>>>>><<<<<>>>>> PHASE TWO")
      val errors = ichemist.errors.exe

      errors.size should be > 0

      errors.collectFirst {
        case Error(Names(_, _, u)) => u
      } should be (Some(IntegrationFixtures.target03.uri))
    }
  }

  // it should "be monitoring 1 but not 2" in {
  //   val U2 = IntegrationFixtures.target02.uri
  //   val U3 = IntegrationFixtures.target03.uri

  //   val state = platform.config.statefulRepository.stateMaps.get
  //   log.debug("repository states: " + state)
  //   log.debug("unmonitored: " + state.lookup(TargetState.Problematic))
  //   state.lookup(TargetState.Problematic).get.lookup(U3).map(_.msg.target.uri) should be (Some(U3))
  //   state.lookup(TargetState.Monitored).get.lookup(U2).map(_.msg.target.uri) should be (Some(U2))
  // }

  /// must be the last thing
  it should "complete the testing" in {
    enterBarrier(Finished)
  }

}
