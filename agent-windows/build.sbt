
import oncue.build._

OnCue.baseSettings

Custom.testing

Custom.compilation

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder" %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.svc.knobs" %% "core"                   % V.knobs
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "agent" / "src" / "main" / "scala"
