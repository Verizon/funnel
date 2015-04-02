
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

libraryDependencies += "oncue.svc.knobs" %% "core" % V.knobs

name in Universal := "flask"

fork in Test := true

mainClass in Revolver.reStart := Some("funnel.flask.Main")
