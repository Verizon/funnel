package funnel
package flask

import java.io.File
import journal.Logger
import scalaz.std.option._
import scala.concurrent.duration._
import scalaz.syntax.applicative._
import knobs.{Config,ClassPathResource,FileResource,Required,Optional}

object Main {
  def main(args: Array[String]): Unit = {
    val log = Logger[Main.type]

    /**
     * Accepting argument on the command line is really just a
     * convenience for testing and ad-hoc ops trial of the agent.
     *
     * Configs are loaded in order; LAST WRITER WINS, as configs
     * are reduced right to left.
     */
    val options: Options = (for {
      a <- knobs.loadImmutable(
        List(
          Required(ClassPathResource("flask/defaults.cfg")),
          Optional(FileResource(new File("/usr/share/oncue/etc/flask.cfg")))
        ) ++ args.toList.map(p => Optional(FileResource(new File(p)))))
    } yield Options.readConfig(a)).run

    log.debug(s"loaded the following configuration settings: $options")

    val I = new Instruments(1.minute)

    val app = new Flask(options, I)
  }
}
