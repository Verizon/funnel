import sbt._, Keys._

object Build extends Build {
  import Dependencies._
  import oncue.build._

  lazy val buildSettings =
    Defaults.defaultSettings ++
    OnCue.baseSettings ++
    ScalaCheck.settings ++ Seq(
      ContinuousIntegration.produceCoverageReport := false,
      organization := "intelmedia.ws.funnel",
      scalaVersion := "2.10.3",
      scalacOptions := Seq(
        "-feature",
        "-language:postfixOps",
        "-language:implicitConversions"))

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
      compile(algebird))

  lazy val http = Project("http", file("http"))
    .settings(buildSettings:_*)
    .settings(libraryDependencies ++= compile(argonaut))
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
    .settings(Deployable.settings:_*)
    .settings(RPM.settings:_*)
    .settings(
      libraryDependencies ++=
        compile(scopt) ++
        compile(logs3),
      name in Universal := "utensil",
      mappings in Universal += {
        file("utensil/etc/logback.xml") -> "etc/logback.xml"
      }
    ).dependsOn(core, http, riemann)
}
