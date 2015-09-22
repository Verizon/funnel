package funnel
package flask

import scala.concurrent.duration._
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import elastic.ElasticCfg

object Main {
  import java.io.File
  import knobs.{ ClassPathResource, Config, FileResource, Required }

  val options: Options = (for {
    a <- knobs.loadImmutable(List(Required(
      FileResource(new File("/usr/share/oncue/etc/flask.cfg")) or
        ClassPathResource("oncue/flask.cfg"))))
    b <- knobs.aws.config
  } yield Options.readConfig(a ++ b)).run

  val I = new Instruments(1.minute)

  val app = new Flask(options, I)

  def main(args: Array[String]) = app.run(args)
}
