import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

libraryDependencies ++= Seq (
  "oncue.svc.knobs"      %% "core"       % V.knobs,
  "intelmedia.ws.common" %% "logging-s3" % "10.+"
)

fork in Test := true
