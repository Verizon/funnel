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
  val Startup  = "startup"
  val Deployed = "deployed"
  val Finished = "finished"
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

import akka.remote.testconductor.RoleName

class MultiNodeSample extends MultiNodeSpec(MultiNodeSampleConfig)
  with STMultiNodeSpec
  with ImplicitSender {
  import MultiNodeSampleConfig._

  def deployTarget(role: RoleName, port: Int) =
    runOn(role){
      IntegrationTarget.start(port)
      enterBarrier(Deployed)
    }

  def deployFlask(role: RoleName, opts: flask.Options) =
    runOn(role){
      IntegrationFlask.start(opts)
      enterBarrier(Deployed)
    }

  def initialParticipants =
    roles.size

  it should "wait for all nodes to enter startup barrier" in {
    enterBarrier(Startup)
  }

  it should "send to and receive from a remote node" in {
    runOn(chemist01) {
      enterBarrier(Deployed)

      println(">>>>>>>>>>>>>> GO CHEMIST")

      true should equal (true)
    }

    deployTarget(target01, 4001)

    deployTarget(target02, 4002)

    deployTarget(target03, 4003)

    deployFlask(flask01, IntegrationFixtures.flask1Options)

    enterBarrier(Finished)
  }
}
