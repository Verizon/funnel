
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

fork in (run in Test) := true

resolvers += "oncue" at "http://dl.bintray.com/oncue/releases"

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % V.dispatch,
  "oncue.knobs"             %% "core"          % V.knobs
)

scalacOptions += "-language:postfixOps"
