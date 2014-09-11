
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

fork in test := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "s3-appender"  % "6.0.2",
  "oncue.svc.knobs"      %% "core"         % "1.1.+"
)

name in Universal := "flask"

mappings in Universal ++= Seq(
  file("flask/src/main/resources/oncue/flask.cfg") -> "etc/flask.cfg"
)

mainClass in Revolver.reStart := Some("intelmedia.ws.funnel.flask.Main")
