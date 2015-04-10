
import oncue.build._

OnCue.baseSettings

libraryDependencies ++= Seq(
  "com.amazonaws"      % "aws-java-sdk"      % "1.8.6",
  "org.scalaz"        %% "scalaz-concurrent" % "7.1.0",
  "org.scalaz.stream" %% "scalaz-stream"     % "0.8-SNAPSHOT"
)
