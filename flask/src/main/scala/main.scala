package funnel
package flask

import scalaz.std.option._
import scala.concurrent.duration._
import scalaz.syntax.applicative._

object Main {
  import java.io.File
  import knobs.{Config,FileResource,Required,Optional}

  def main(args: Array[String]): Unit = {
    /**
     * Accepting argument on the command line is really just a
     * convenience for testing and ad-hoc ops trial of the agent.
     *
     * Configs are loaded in order; LAST WRITER WINS, as configs
     * are reduced right to left.
     */
    val options: Options = (for {
      a <- knobs.loadImmutable(
        List(Required(FileResource(new File("/usr/share/oncue/etc/flask.cfg")))
          ) ++ args.toList.map(p => Optional(FileResource(new File(p)))))
      b <- knobs.aws.config
    } yield Options.readConfig(a ++ b)).run

    val I = new Instruments(1.minute)

    val app = new Flask(options, I)
  }
}
