
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

libraryDependencies ++= Seq("org.zeromq" % "jeromq" % "0.3.4",
                            "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test",
                            "oncue.ermine"    %% "scala-parsers"        % "0.2.1-1"
                           )

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "scala"

scalaSource in Test := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "test"
