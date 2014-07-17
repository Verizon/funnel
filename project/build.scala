import sbt._, Keys._

object Build extends Build {
  import Dependencies._
  import oncue.build._

  lazy val buildSettings =
    Defaults.defaultSettings ++
    OnCue.baseSettings ++
    ScalaCheck.settings ++
    ScalaTest.settings ++ Seq(
      ContinuousIntegration.produceCoverageReport := false,
      organization := "intelmedia.ws.funnel",
      scalaVersion := "2.10.3",
      addCompilerPlugin("org.brianmckenna" %% "wartremover" % "0.9"),
      scalacOptions := Seq(
        "-feature",
        "-Ywarn-value-discard",
        "-language:postfixOps",
        "-language:implicitConversions"))
      //scalacOptions in (Compile) += "-P:wartremover:traverser:org.brianmckenna.wartremover.warts.NonUnitStatements"


  lazy val root = Project(
    id = "funnel",
    base = file("."),
    settings = buildSettings ++ Seq(
      publishArtifact in (Compile, packageBin) := false,
      publish := (),
      publishLocal := ()
    )
  ).aggregate(core, http, riemann, utensil)

  lazy val core = Project("core", file("core"))
    .settings(buildSettings:_*)
    .settings(name := "core")
    .settings(libraryDependencies ++=
      compile(scalazstream) ++
      compile(algebird) ++
      compile(sigar) ++
      compile(log4jslf))

  lazy val http = Project("http", file("http"))
    .settings(buildSettings:_*)
    .settings(
      libraryDependencies ++= compile(argonaut),
      fork in (run in Test) := true
    )
    .dependsOn(core)

  lazy val riemann = Project("riemann", file("riemann"))
    .settings(buildSettings:_*)
    .settings(resolvers += "clojars.org" at "http://clojars.org/repo")
    .settings(libraryDependencies ++=
      compile(riemannapi) ++
      compile(logback))
    .dependsOn(core)

  import com.typesafe.sbt.SbtNativePackager._
  import com.typesafe.sbt.packager.Keys._

  lazy val utensil = Project("utensil", file("utensil"))
    .settings(buildSettings:_*)
    .settings(Bundle.settings:_*)
    .settings(
      libraryDependencies ++=
        compile(logs3) ++
        compile(aws) ++
        compile(knobs),
      name in Universal := "utensil"
    ).dependsOn(core, http, riemann)
}
