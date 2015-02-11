
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

artifact in makePom := Artifact.pom("funnel-agent").copy(classifier = Some(name.value))

mappings in Universal ++= Seq(
  file("agent/deploy/agent/etc/agent.cfg") -> "etc/agent.cfg"
)

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"  %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.svc.knobs" %% "core"                    % V.knobs,
  "io.netty"         % "netty-handler"           % "4.0.25.Final",
  "io.netty"         % "netty-codec"             % "4.0.25.Final"
)

mainClass in Revolver.reStart := Some("oncue.svc.funnel.agent.Main")
