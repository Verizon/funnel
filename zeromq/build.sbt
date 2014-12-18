
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.3.4"
)
