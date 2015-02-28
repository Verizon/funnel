
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

// pure-java implementation:
// libraryDependencies += "org.zeromq" % "jeromq" % "0.3.4"
// native c++ implementation with jni:
libraryDependencies += "org.zeromq" % "jzmq" % "3.1.0"
