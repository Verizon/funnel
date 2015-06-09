package sample.multinode

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

import akka.remote.testkit.MultiNodeConfig

object MultiNodeSampleConfig extends MultiNodeConfig {
  val chemist01 = role("chemist01")
  val flask01   = role("flask01")
  // val flask02   = role("flask02")
  val target01  = role("target01")
  // val target02  = role("target02")
  // val target03  = role("target03")
}

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.actor.{Props,Actor}

class MultiNodeSampleSpecMultiJvmNode1 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode2 extends MultiNodeSample
class MultiNodeSampleSpecMultiJvmNode3 extends MultiNodeSample
// class MultiNodeSampleSpecMultiJvmNode4 extends MultiNodeSample
// class MultiNodeSampleSpecMultiJvmNode5 extends MultiNodeSample

class MultiNodeSample extends MultiNodeSpec(MultiNodeSampleConfig)
  with STMultiNodeSpec
  with ImplicitSender {

  import MultiNodeSampleConfig._

  def initialParticipants = roles.size

  it should "wait for all nodes to enter a barrier" in {
    enterBarrier("startup")
  }

  it should "send to and receive from a remote node" in {
    runOn(chemist01) {
      enterBarrier("deployed")

      println(">>>>>>>>>>>>>> GO CHEMIST")

      // true should equal (true)

      // val ponger = system.actorSelection(node(node2) / "user" / "ponger")
      // ponger ! "ping"
      // expectMsg("pong")
    }

    runOn(flask01) {
      Thread.sleep(500)
      println(">>>>>>> FLASK READ")
      // system.actorOf(Props[Ponger], "ponger")
      enterBarrier("deployed")
    }

    enterBarrier("finished")
  }
}
