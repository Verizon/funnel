package oncue.svc.funnel.chemist

import oncue.svc.funnel.aws._
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import scala.collection.JavaConverters._
import scalaz.concurrent.Task

object Sandbox {
  def main(args: Array[String]): Unit = {
    val K = sys.env("AWS_ACCESS_KEY")
    val S = sys.env("AWS_SECRET_KEY")

    val C = ASG.client(new BasicAWSCredentials(K, S))
    val E = EC2.client(new BasicAWSCredentials(K, S))

    import Machines._

    // val exe = listAll(C,E).map(_.filter(filters.funnels))

    val exe = listFunnels(C,E)

    exe.run.foreach(println)

    println("END")
  }
}
