package oncue.svc.funnel.chemist

import oncue.svc.funnel.aws._
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import scala.collection.JavaConverters._
import scalaz.concurrent.Task
import scalaz.==>>

object Sandbox {
  def main(args: Array[String]): Unit = {
    implicit val log = journal.Logger("chemist")

    val K = sys.env("AWS_ACCESS_KEY")
    val S = sys.env("AWS_SECRET_KEY")

    val C = ASG.client(new BasicAWSCredentials(K, S))
    val E = EC2.client(new BasicAWSCredentials(K, S))

    val R = new StatefulRepository(E)

    println {
      // R.increaseCapacity("i-xxx").run
      // Deployed.lookupMany(List("i-114deb3c","i-4df70d61"))(E).run
      val list = Deployed.list(C, E).run

      s"==================== ${list.filter(Deployed.filter.flasks)}"
    }
  }


  // def main(args: Array[String]): Unit = {
  //   import Sharding.{Distribution,Target}

  //   implicit val log = journal.Logger("chemist")


  //   val K = sys.env("AWS_ACCESS_KEY")
  //   val S = sys.env("AWS_SECRET_KEY")

  //   val C = ASG.client(new BasicAWSCredentials(K, S))
  //   val E = EC2.client(new BasicAWSCredentials(K, S))

  //   // val exe = listAll(C,E).map(_.filter(filters.funnels))

  //   // val exe = Deployed.list(C,E)
  //   val I = new Ref[InstanceM](==>>())
  //   I.update(_.insert("i-123",
  //     Instance(
  //       id = "i-123",
  //       location = Location(Some("google.com"), datacenter = "us-east-1a"),
  //       firewalls = Seq.empty
  //     )
  //   ))

  //   val D = new Ref[Distribution](Distribution.empty)
  //   D.update(_.insert("i-123", Set()))

  //   val T = Set(Target("foo",SafeURL("http://timperrett.com")))

  //   val exe = Sharding.distribute(T)(D,I)

  //   println(exe.run)

  //   // .foreach(println)

  //   println("END")
  // }
}
