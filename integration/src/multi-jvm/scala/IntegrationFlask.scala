package funnel
package integration

import flask.{Flask,Options => FlaskOptions}
import journal.Logger

object IntegrationFlask {
  import concurrent.duration._

  val log = Logger[IntegrationFlask.type]

  def start(options: FlaskOptions): () => Unit = {
    val I = new Instruments()
    val app = new Flask(options, I)
    app.unsafeRun()
    () => app.shutdown()
  }
}
