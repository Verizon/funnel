
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

libraryDependencies ++= Seq(
  "org.zeromq"       % "jeromq"               % "0.3.4",
  "org.scodec"      %% "scodec-core"          % "1.7.1",
  "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test",
  "oncue.ermine"    %% "scala-parsers"        % V.ermine
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "scala"

scalaSource in Test := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "test"
