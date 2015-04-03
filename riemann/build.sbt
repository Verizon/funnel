
import oncue.build._

OnCue.baseSettings

ScalaCheck.settings

fork in (run in Test) := true

libraryDependencies += "com.aphyr" % "riemann-java-client" % "0.2.9" exclude("com.codahale.metrics","metrics-core")

scalacOptions += "-language:postfixOps"
