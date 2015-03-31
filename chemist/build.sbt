
import oncue.build._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

fork in test := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common"    %% "logging-s3"              % "8.0.+",
  "oncue.svc.knobs"         %% "core"                    % V.knobs,
  "net.databinder.dispatch" %% "dispatch-core"           % "0.11.2",
  "net.databinder"          %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"          %% "unfiltered-netty-server" % V.unfiltered
)
