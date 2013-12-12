import sbt._, Keys._

object Dependencies {

  def compile   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile")
  def provided  (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
  def test      (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "test")
  def runtime   (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "runtime")
  def container (deps: ModuleID*): Seq[ModuleID] = deps map (_ % "container")

  val scalatest    = "org.scalatest"     %% "scalatest"           % "1.9.2"
  val scalaz       = "org.scalaz"        %% "scalaz-core"         % "7.0.4"
  val scalazstream = "org.scalaz.stream" %% "scalaz-stream"       % "0.1"
  val algebird     = "com.twitter"       %% "algebird-core"       % "0.3.0"
  val scalacheck   = "org.scalacheck"    %% "scalacheck"          % "1.10.0"
  val argonaut     = "io.argonaut"       %% "argonaut"            % "6.0.1"
  val riemannapi   = "com.aphyr"          % "riemann-java-client" % "0.2.8" exclude("com.yammer.metrics","metrics-core")
  val logback      = "ch.qos.logback"     % "logback-classic"     % "1.0.+"
  val scopt        = "com.github.scopt"  %% "scopt"               % "3.2.0"
}
