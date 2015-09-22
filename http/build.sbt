
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Custom.resources

fork in (run in Test) := true

libraryDependencies += "io.argonaut" %% "argonaut" % "6.1"
