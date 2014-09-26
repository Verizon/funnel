
import oncue.build._

OnCue.baseSettings

BuildMetadata.settings

ScalaCheck.settings

ScalaTest.settings

ContinuousIntegration.produceCoverageReport := false

scalacOptions := Compilation.flags.filterNot(_ == "-Xlint") ++ Seq(
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.scalaz.stream"    %% "scalaz-stream"       % "0.5",
  "com.twitter"          %% "algebird-core"       % "0.8.0",
  "org.fusesource"        % "sigar"               % "1.6.4" classifier("native") classifier("") exclude("log4j", "log4j"),
  "org.slf4j"             % "log4j-over-slf4j"    % "1.7.+" // SIGAR requires the log4j legacy API
)

addCompilerPlugin("org.brianmckenna" %% "wartremover" % "0.9")
