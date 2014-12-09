
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.3.4"
)
