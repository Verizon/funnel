
import oncue.build._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

fork in test := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common"    %% "logging-s3"              % "10.+",
  "oncue.svc.knobs"         %% "core"                    % V.knobs,
  "net.databinder.dispatch" %% "dispatch-core"           % V.dispatch,
  "net.databinder"          %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"          %% "unfiltered-netty-server" % V.unfiltered,
  "org.webjars.bower"        % "angular"                 % "1.3.15"
)
