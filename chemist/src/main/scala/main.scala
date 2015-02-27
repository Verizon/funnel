package funnel
package chemist

import knobs._
import java.io.File

object Main {
  def main(args: Array[String]): Unit = {
    val config = (for {
      a <- knobs.loadImmutable(Required(
        FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
        ClassPathResource("oncue/chemist.cfg")) :: Nil)
      b <- knobs.aws.config
    } yield Config.readConfig(a ++ b)).run

    Server.start(config).run

    // this should probally be called to release
    // the underlying resources.
    // dispatch.Http.shutdown()
  }
}