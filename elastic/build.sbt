
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "oncue.svc.knobs"      %% "core"          % "2.0.+"
)

scalacOptions += "-language:postfixOps"

