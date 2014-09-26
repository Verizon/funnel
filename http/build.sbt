
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

fork in (run in Test) := true

libraryDependencies += "oncue.argonaut" %% "argonaut" % "6.0.7"
