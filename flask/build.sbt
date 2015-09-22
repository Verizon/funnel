import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.resources

Custom.revolver

libraryDependencies += "oncue.knobs" %% "core" % V.knobs

fork in Test := true
