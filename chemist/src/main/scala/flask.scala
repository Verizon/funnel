package funnel
package chemist

import scalaz.Order
import scalaz.std.string._

case class FlaskID(value: String) extends AnyVal

object FlaskID {
  implicit val flaskIdOrder: Order[FlaskID] = implicitly[Order[String]].contramap[FlaskID](_.value)
}

case class Flask(id: FlaskID,
                 location: Location,
                 telemetry: Location)

object Flask {
  import FlaskID._
  implicit val flaskOrder: Order[Flask] = implicitly[Order[FlaskID]].contramap[Flask](_.id)
}
