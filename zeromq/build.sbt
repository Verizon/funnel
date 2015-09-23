
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.resources

// pure-java implementation:
// libraryDependencies += "org.zeromq" % "jeromq" % "0.3.4"
// native c++ implementation with jni:
libraryDependencies ++= Seq(
  "org.zeromq"      % "jzmq"                  % "3.1.0",
  "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test",
  "oncue.ermine"    %% "scala-parsers"        % V.ermine
)
