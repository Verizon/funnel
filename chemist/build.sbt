
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "s3-appender"  % "6.0.2",
  "com.amazonaws"         % "aws-java-sdk" % "1.7.9",
  "oncue.svc.knobs"      %% "core"         % "0.1.+"
)

name in Universal := "chemist"

// mappings in Universal ++= Seq(
//   file("utensil/etc/flask.cfg") -> "etc/flask.cfg"
// )

// mainClass in Revolver.reStart := Some("intelmedia.ws.funnel.flask.Main")
