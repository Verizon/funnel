import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

libraryDependencies += "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test"


initialCommands in console := """
import funnel._
import funnel.zeromq._
import funnel.messages._
import scalaz.stream._
import scalaz.concurrent._
import Process._
import java.net.URI
import Telemetry._
import sockets._

val U1 = new java.net.URI("ipc:///tmp/console.socket")
val S = async.signalOf(true)
"""
