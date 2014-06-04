import sbt._, Keys._

object Dependencies {

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val scalatest    = "org.scalatest"        %% "scalatest"           % "1.9.2"
  val scalazstream = "org.scalaz.stream"    %% "scalaz-stream"       % "0.3"
  val algebird     = "com.twitter"          %% "algebird-core"       % "0.3.0"
  val scalacheck   = "org.scalacheck"       %% "scalacheck"          % "1.10.0"
  val argonaut     = "io.argonaut"          %% "argonaut"            % "6.0.1"
  val riemannapi   = "com.aphyr"             % "riemann-java-client" % "0.2.8" exclude("com.yammer.metrics","metrics-core")
  val logback      = "ch.qos.logback"        % "logback-classic"     % "1.0.+"
  val logs3        = "intelmedia.ws.common" %% "s3-appender"         % "6.0.2"
  val aws          = "com.amazonaws"         % "aws-java-sdk"        % "1.7.9"
  val knobs        = "oncue.svc.knobs"      %% "core"                % "0.1.29"

  // SIGAR requires the log4j legacy API
  val log4jslf     = "org.slf4j"             % "log4j-over-slf4j"    % "1.7.+"
  val sigar        = "org.fusesource"        % "sigar"               % "1.6.4" classifier("native") classifier("") exclude("log4j", "log4j")
}
