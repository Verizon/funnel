package funnel
package chemist

import knobs._

object Main {
  def main(args: Array[String]): Unit = {
    // (for {
    //   a <- knobs.loadImmutable(Required(
    //     FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
    //     ClassPathResource("oncue/chemist.cfg")) :: Nil)
    //   b <- knobs.aws.config
    //   _ <- exe.run(Config.readConfig(a ++ b))
    // } yield ()).run

    // alias the `Server` API and what constitutes a chemst server
    // val S = Server
    // provide an interpreter for that server
    // val I = Server0

    // new Chemist(I, 9000).start()
    // this should probally be called to release
    // the underlying resources.
    // dispatch.Http.shutdown()
  }
}