
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-filter"       % "0.8.3",
  "net.databinder" %% "unfiltered-netty-server" % "0.8.3",
  "oncue.svc.knobs" %% "core"                   % "2.0.+"
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "agent" / "src" / "main" / "scala"
