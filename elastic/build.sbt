
import oncue.build._

OnCue.baseSettings

Custom.resources

ScalaCheck.settings

ScalaTest.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % V.dispatch,
  "oncue.knobs"             %% "core"          % V.knobs
)

scalacOptions += "-language:postfixOps"
