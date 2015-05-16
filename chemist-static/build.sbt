
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

name in Universal := "chemist-static"

fork in Test := true

mainClass in Compile := Some("funnel.chemist.static.Main")

mainClass in Revolver.reStart := Some("funnel.chemist.static.Main")
