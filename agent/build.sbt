
import oncue.build._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Bundle.settings

Revolver.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.compilation

normalizedName := "funnel-agent"

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % "0.8.3",
  "net.databinder"  %% "unfiltered-netty-server" % "0.8.3",
  "oncue.svc.knobs" %% "core"                    % "2.0.+",
  "io.netty"         % "netty-handler"           % "4.0.25.Final",
  "io.netty"         % "netty-codec"             % "4.0.25.Final"
)

mainClass in Revolver.reStart := Some("oncue.svc.funnel.agent.Main")
