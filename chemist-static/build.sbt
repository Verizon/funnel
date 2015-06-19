
import oncue.build._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

fork in Test := true

mainClass in Compile := Some("funnel.chemist.static.Main")

mainClass in Revolver.reStart := Some("funnel.chemist.static.Main")
