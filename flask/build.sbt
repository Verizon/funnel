
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "logging-s3"  % "9.1.+",
  "oncue.svc.knobs"      %% "core"        % V.knobs,
  "org.typelevel"        %% "scodec-core" % "1.6.+"
)

name in Universal := "flask"

fork in Test := true

mappings in Universal ++= Seq(
  file("flask/src/main/resources/oncue/flask.cfg") -> "etc/flask.cfg"
)

mainClass in Revolver.reStart := Some("funnel.flask.Main")
