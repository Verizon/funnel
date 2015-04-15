
import oncue.build._

OnCue.baseSettings

resolvers += "Runar Bintray" at "https://dl.bintray.com/runarorama/maven"

libraryDependencies ++= Seq(
  "com.amazonaws"      % "aws-java-sdk"      % "1.8.6",
  "org.scalaz"        %% "scalaz-concurrent" % "7.1.0",
  "org.scalaz.stream" %% "scalaz-stream"     % "0.8r.1"
)
