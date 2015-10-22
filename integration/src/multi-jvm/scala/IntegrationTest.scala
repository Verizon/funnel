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

object MultiNodeIntegrationConfig extends MultiNodeConfig {
  /**
   * important thing to note here is that each role needs to be assigned
   * to a specific jvm. If its not, the test will complain there is not
   * enough nodes to run the test suite.
   */
  val chemist01 = role("chemist01")
  val flask01   = role("flask01")
  val flask02   = role("flask02")
  val target01  = role("target01")
  val target02  = role("target02")
  val target03  = role("target03")

  //////// barriers /////////
  val Startup      = "startup"
  // everything is deployed and running
  val Deployed     = "deployed"
  // metrics are flowing around and we're monitoring for
  // a period of time
  val PhaseOne     = "phase-one"
  // time to tear some shit down and track errors!
  val PhaseTwo     = "phase-two"
  // test is over
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

class MultiNodeIntegrationSpecMultiJvmNode1 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode2 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode3 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode4 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode5 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode6 extends MultiNodeIntegration

//////////////////////// Actual Test //////////////////////////

import akka.remote.testconductor.{RoleName,Controller}
import concurrent.{Future,ExecutionContext}
import chemist.{Server,FlaskID,PlatformEvent}
import java.util.concurrent.{CountDownLatch,TimeUnit}
import concurrent.duration._

class MultiNodeIntegration extends MultiNodeSpec(MultiNodeIntegrationConfig)
  with STMultiNodeSpec
  with ImplicitSender {
  import MultiNodeIntegrationConfig._
  import PlatformEvent._

  def printObnoxiously[A](a: => A): Unit = {
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println(a)
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<")
  }

  def fetch(path: String) = {
    import dispatch._, Defaults._

    val svc = url(s"http://127.0.0.1:64529${path}")
    val json = http(svc OK as.String)
    scala.concurrent.Await.result(json, 2.seconds)
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

  // def countForState(s: TargetLifecycle.TargetState): Int =
  //   ichemist.states.map(_.apply(s).keys.size).exe

  it should "wait for all nodes to enter startup barrier" in {
    enterBarrier(Startup)
  }

  it should "setup the world" in {
    runOn(chemist01){
      enterBarrier(Deployed)
      // just fork the shit out of it so it doesnt block our test.
      Future(Server.unsafeStart(new Server(ichemist, platform))
        )(ExecutionContext.Implicits.global)

      Thread.sleep(5.seconds.toMillis) // give the system time to do its thing

      enterBarrier(PhaseOne)
    }

    deployTarget(target01, 4001)

    deployTarget(target02, 4002)

    deployTarget(target03, 4003, Some(5.seconds))

    deployFlask(flask01, IntegrationFixtures.flask1Options)

    deployFlask(flask02, IntegrationFixtures.flask2Options)
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
      Thread.sleep(2.seconds.toMillis) // give chemist time to get the messages from all flasks
      // just adding this to make sure that in future, the json does not get fubared.
      // fetch("/distribution") should equal ("""[{"targets":[{"urls":["http://localhost:4001/stream/now"],"cluster":"target01"},{"urls":["http://localhost:4003/stream/now"],"cluster":"target03"},{"urls":["http://localhost:4002/stream/now"],"cluster":"target02"}],"shard":"flask1"}]""")
      printObnoxiously(fetch("/distribution"))
      // printObnoxiously(fetch("/lifecycle/states"))
      printObnoxiously(fetch("/shards/flask1/sources"))
      printObnoxiously(fetch("/shards/flask1/distribution"))

      // countForState(TargetState.Monitored) should equal (7)

      // countForState(TargetState.Assigned) should equal (0)

      // countForState(TargetState.DoubleMonitored) should equal (0)
    }
  }

  it should "have events in the history" in {
    runOn(chemist01){
      val history = ichemist.platformHistory.exe
      history.size should be > 0
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
