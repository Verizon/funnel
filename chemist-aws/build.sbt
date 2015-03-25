
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

Custom.compilation

Coverage.settings

name in Universal := "chemist-aws"

fork in Test := true

mainClass in Compile := Some("funnel.chemist.aws.Main")

mainClass in Revolver.reStart := Some("funnel.chemist.aws.Main")
