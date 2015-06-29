import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

resolvers += "oncue" at "http://dl.bintray.com/oncue/releases"

libraryDependencies += "oncue.knobs" %% "core" % V.knobs

fork in Test := true
