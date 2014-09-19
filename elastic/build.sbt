
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.0.+"
)

scalacOptions += "-language:postfixOps"

