
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "com.aphyr" % "riemann-java-client" % "0.2.9" exclude("com.yammer.metrics","metrics-core"),
  "ch.qos.logback" % "logback-classic" % "1.0.+"
)

scalacOptions += "-language:postfixOps"
