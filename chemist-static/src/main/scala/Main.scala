package funnel
package chemist
package static

import java.io.File
import journal.Logger
import concurrent.duration._
import scalaz.concurrent.Task
import http.MonitoringServer
import knobs.{FileResource,ClassPathResource,Optional,Pattern,Required}

object Main {
  def main(args: Array[String]): Unit = {
    val log = Logger[Main.type]

    log.info(Banner.text)

    val chemist = new StaticChemist[Static]

    val k = knobs.load(
      Required(ClassPathResource("oncue/chemist.cfg")) ::
      Optional(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))) :: Nil).run

    val s = new Static { val config = Config.readConfig(k).run }

    // TIM: best I can tell, this didnt actually work beforehand
    // as we're running this on a  prototype and it doesn't
    // seem to reload.
    // k.subscribe(Pattern("*.*"), {
    //   case (name, optvalue) => chemist.bootstrap.run(new Static {
    //     val config = Config.readConfig(k).run
    //   })
    // })

    val monitoring = MonitoringServer.start(Monitoring.default, 5775)

    // this is the edge of the world and will just block until its stopped
    Server.unsafeStart(new Server(chemist, s))

    // if we reach these then the server process has stopped and we need
    // to cleanup the associated resources.
    monitoring.stop()
    dispatch.Http.shutdown()
  }
}
