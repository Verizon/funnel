
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

Bundle.settings

Revolver.settings

ScalaTest.settings

Custom.compilation

fork in test := true

libraryDependencies ++= Seq(
  "intelmedia.ws.common"    %% "logging-s3"              % "8.0.+",
  "oncue.svc.knobs"         %% "core"                    % V.knobs,
  "net.databinder.dispatch" %% "dispatch-core"           % "0.11.2",
  "net.databinder"          %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"          %% "unfiltered-netty-server" % V.unfiltered
)

name in Universal := "chemist"

mainClass in Compile := Some("funnel.chemist.Main")

mainClass in Revolver.reStart := Some("funnel.chemist.Main")

scalacOptions += "-language:postfixOps"
