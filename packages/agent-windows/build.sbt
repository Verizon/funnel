
import oncue.build._

OnCue.baseSettings

Bundle.settings

Custom.testing

Custom.compilation

name := "agent-windows"

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"  %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.knobs"     %% "core"                    % V.knobs,
  "io.netty"         % "netty-handler"           % "4.0.25.Final",
  "io.netty"         % "netty-codec"             % "4.0.25.Final",
  "com.github.cjmx" %% "cjmx"                    % "2.2.+" exclude("org.scala-sbt","completion") exclude("com.google.code.gson","gson")
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / ".." / "agent" / "src" / "main" / "scala"

mappings in Universal ++= Seq(
  file("packages/agent/deploy/etc/agent.cfg") -> "etc/agent.cfg"
)

unmanagedClasspath in Compile ++= Custom.toolsJar

unmanagedClasspath in Test ++= Custom.toolsJar
