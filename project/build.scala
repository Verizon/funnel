import sbt._, Keys._

object Build extends Build {
  import Dependencies._

  lazy val buildSettings =
    Defaults.defaultSettings ++
    ImBuildPlugin.imBuildSettings ++ Seq(
      organization := "intelmedia.ws.funnel",
      scalaVersion := "2.10.3",
      scalacOptions ++= Seq(
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
    ) ++ ScctPlugin.mergeReportSettings
  ).aggregate(core, http, riemann, utensil)

  lazy val core = Project("core", file("core"))
    .settings(buildSettings:_*)
    .settings(name := "core")
    .settings(libraryDependencies ++=
      compile(scalaz) ++
      compile(scalazstream) ++
      compile(algebird) ++
      test(scalacheck) ++
      test(scalatest))

  lazy val http = Project("http", file("http"))
    .settings(buildSettings:_*)
    .settings(libraryDependencies ++=
      compile(argonaut))
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

  lazy val bundleRpmSettings = Seq(
    maintainer := "Timothy Perrett <timothy.m.perrett@intel.com>",
    packageSummary := "Intel Media Monitoring Utensil",
    packageDescription := """Testing description""",
    name in Rpm := "utensil",
    version in Rpm <<= version apply { sv => sv.split("[^\\d]").filterNot(_.isEmpty).mkString(".") },
    rpmRelease := "1",
    rpmVendor := "intelmedia",
    rpmLicense := Some("Copyright Intel, 2013")
  )

  lazy val bundleZipSettings = Seq(
    name in Universal := "utensil"
  )

  lazy val utensil = Project("utensil", file("utensil"))
    .settings(buildSettings:_*)
    .settings(crossPaths := false) // adding this because its an executable
    .settings(libraryDependencies ++= compile(scopt))
    .settings(deploymentSettings:_*)
    .settings(packageArchetype.java_server:_*)
    .settings(bundleRpmSettings:_*)
    .settings(bundleZipSettings:_*)
    .dependsOn(core, http, riemann)
}
