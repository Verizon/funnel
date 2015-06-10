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

// class ChemistIntMultiJvmFlask1 extends FlatSpec {
//   val options = IntegrationFixtures.flask1Options

//   val I = new funnel.Instruments(1.minute)
//   val app = new funnel.flask.Flask(options, I)

//   Thread.sleep(40000)
//   log.debug("flask shutting down")
// }