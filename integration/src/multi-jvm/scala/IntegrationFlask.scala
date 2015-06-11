package funnel
package integration

import flask.{Flask,Options => FlaskOptions}
import journal.Logger

object IntegrationFlask {
  import concurrent.duration._

  val log = Logger[IntegrationFlask.type]

  def start(options: FlaskOptions): Unit = {
    val I = new Instruments(1.minute)
    val app = new Flask(options, I)
    // TIM: this is an ugly hack from stews testing. Must remove.
    app.run(Array("noretries"))
  }
}
