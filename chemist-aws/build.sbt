
import oncue.build._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

libraryDependencies += "com.amazonaws" % "aws-java-sdk" % "1.8.6"

fork in Test := true

mainClass in Compile := Some("funnel.chemist.aws.Main")

mainClass in Revolver.reStart := Some("funnel.chemist.aws.Main")
