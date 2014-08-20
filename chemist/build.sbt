
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

fork in test := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "s3-appender"  % "6.0.2",
  "com.amazonaws"         % "aws-java-sdk" % "1.7.9",
  "oncue.svc.knobs"      %% "core"         % "1.1.+",
  "oncue.svc.journal"    %% "core"         % "1.0.+"
)

name in Universal := "chemist"

mainClass in Revolver.reStart := Some("oncue.svc.funnel.chemist.Sandbox")

scalacOptions := Compilation.flags.filterNot(_ == "-Xlint") ++ Seq(
  "-language:postfixOps"
)
