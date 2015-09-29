
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Custom.resources

Custom.binaryCompatibility

fork in (run in Test) := true

libraryDependencies += "io.argonaut" %% "argonaut" % "6.1"
