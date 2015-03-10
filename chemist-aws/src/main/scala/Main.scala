package funnel
package chemist
package aws

object Main {
  def main(args: Array[String]): Unit = {

    val chemist = new AwsChemist

    val aws = new Aws {
      val config = (for {
        a <- defaultKnobs
        b <- knobs.aws.config
      } yield Config.readConfig(a ++ b)).run
    }

    Server.start(chemist, aws).run

    // this should probally be called to release
    // the underlying resources.
    // dispatch.Http.shutdown()
  }
}