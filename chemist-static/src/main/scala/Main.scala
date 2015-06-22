package funnel
package chemist
package static

import java.io.File
import scalaz.concurrent.Task
import http.MonitoringServer
import knobs.{FileResource,ClassPathResource,Optional,Pattern,Required}

object Main {
  def main(args: Array[String]): Unit = {

    val chemist = new StaticChemist

    val k = knobs.load(
      Required(ClassPathResource("oncue/chemist.cfg")) ::
      Optional(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))) :: Nil).run
    val s = new Static { val config = Config.readConfig(k).run }

    k.subscribe(Pattern("*.*"), {
      case _ => chemist.bootstrap.run(new Static {
        val config = Config.readConfig(k).run
      })
    })

    val monitoring = MonitoringServer.start(Monitoring.default, 5775)

    // this is the edge of the world and will just block until its stopped
    Server.unsafeStart(chemist, s)

    // if we reach these then the server process has stopped and we need
    // to cleanup the associated resources.
    monitoring.stop()
    dispatch.Http.shutdown()
  }
}
