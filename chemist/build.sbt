
import oncue.build._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

ContinuousIntegration.produceCoverageReport := false

fork in test := true

initialCommands in console := """
import funnel._
import chemist._
import TargetLifecycle._
import TargetState._
import scalaz.concurrent.Task
import scalaz.stream.Process
"""

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "logging-s3"           % "10.+",
  "oncue.svc.knobs"      %% "core"                 % V.knobs,
  "oncue.quiver"         %% "core"                 % "3.1.+",
  "org.http4s"           %% "http4s-blazeclient"   % "0.8.0-SNAPSHOT",
  "org.http4s"           %% "http4s-blazeserver"   % "0.8.0-SNAPSHOT",
  "oncue.typelevel"      %% "shapeless-scalacheck" % "0.4.0" % "test"
)
