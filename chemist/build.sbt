
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
  "intelmedia.ws.common"    %% "logging-s3"              % "10.+",
  "oncue.svc.knobs"         %% "core"                    % V.knobs,
  "net.databinder.dispatch" %% "dispatch-core"           % V.dispatch,
  "net.databinder"          %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"          %% "unfiltered-netty-server" % V.unfiltered,
  "oncue"                   %% "quiver"                  % "3.0.+",
  "oncue.typelevel"         %% "shapeless-scalacheck"    % "0.4.0" % "test"
)
