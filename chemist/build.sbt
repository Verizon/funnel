
import oncue.build._

OnCue.baseSettings

Revolver.settings

ScalaTest.settings

Custom.compilation

ContinuousIntegration.produceCoverageReport := false

fork in test := true

initialCommands in console := """
import funnel._
import chemist._
import TargetLifecycle._
import TargetState._
import scalaz.concurrent.Task
import scalaz.stream.Process
"""


resolvers ++= Seq(Resolver.sonatypeRepo("releases"), Resolver.sonatypeRepo("snapshots"))

libraryDependencies ++= Seq(
  "intelmedia.ws.common" %% "logging-s3"           % "10.+",
  "oncue.svc.knobs"      %% "core"                 % V.knobs,
  "oncue.quiver"         %% "core"                 % "3.1.+",
  "org.http4s"           %% "http4s-blazeclient"   % "0.8.1",
  "org.http4s"           %% "http4s-blazeserver"   % "0.8.1",
  "org.http4s"           %% "http4s-argonaut"      % "0.8.1",
  "org.http4s"           %% "http4s-dsl"           % "0.8.1",
  "com.google.guava"         % "guava"                   % "18.0",
  "com.google.code.findbugs" % "jsr305"                  % "1.3.+", // needed to provide class javax.annotation.Nullable
  "oncue.typelevel"      %% "shapeless-scalacheck" % "0.4.0" % "test"
)
