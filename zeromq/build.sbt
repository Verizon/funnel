
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

libraryDependencies ++= Seq(
  "org.zeromq" % "jeromq" % "0.3.4",
  "org.typelevel" %% "scodec-core" % "1.5.0",
  "org.msgpack" % "msgpack-core" % "0.7.0-M5" % "test"
)
