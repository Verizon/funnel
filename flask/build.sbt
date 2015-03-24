
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "logging-s3" % "8.0.+",
  "oncue.svc.knobs"      %% "core"       % V.knobs
)

name in Universal := "flask"

(scalacOptions in MultiJvm) += "-language:postfixOps"

mappings in Universal ++= Seq(
  file("flask/src/main/resources/oncue/flask.cfg") -> "etc/flask.cfg"
)

mainClass in Revolver.reStart := Some("funnel.flask.Main")
