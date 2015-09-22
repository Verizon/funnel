import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.resources

libraryDependencies += "oncue.knobs" %% "core" % V.knobs

fork in Test := true
