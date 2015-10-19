package funnel
package chemist

trait FlaskCommand
object FlaskCommand {
  case class Monitor(flask: Flask, target: Seq[Target]) extends FlaskCommand
  case class Unmonitor(flask: Flask, target: Seq[Target]) extends FlaskCommand
  case object Report extends FlaskCommand
}
