
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % V.dispatch,
  "oncue.svc.knobs"         %% "core"          % V.knobs
)

scalacOptions += "-language:postfixOps"
