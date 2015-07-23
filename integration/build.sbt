import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.compilation

Publishing.ignore

libraryDependencies += "com.typesafe.akka" %% "akka-multi-node-testkit" % "2.3.11" % "test"
