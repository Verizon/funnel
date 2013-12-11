import sbt._, Keys._

object Build extends Build {
  import Dependencies._

  lazy val buildSettings =
    Defaults.defaultSettings ++
    ImBuildPlugin.imBuildSettings ++ Seq(
      organization := "intelmedia.ws.monitoring",
      scalaVersion := "2.10.3",
      scalacOptions ++= Seq(
        "-feature",
        "-language:postfixOps",
        "-language:implicitConversions"))

  lazy val root = Project(
    id = "monitoring",
    base = file("."),
    settings = buildSettings ++ Seq(
      publishArtifact in (Compile, packageBin) := false,
      publish := (),
      publishLocal := ()
    ) ++ ScctPlugin.mergeReportSettings
  ).aggregate(funnel, funnelhttp, funnelcli, funnelriemann)

  lazy val funnel = Project("funnel", file("funnel"))
    .settings(buildSettings:_*)
    .settings(name := "funnel")
    .settings(libraryDependencies ++=
      compile(scalaz) ++
      compile(scalazstream) ++
      compile(algebird) ++
      test(scalacheck) ++
      test(scalatest))

  lazy val funnelhttp = Project("funnel-http", file("funnel-http"))
    .settings(buildSettings:_*)
    .settings(libraryDependencies ++=
      compile(argonaut))
    .dependsOn(funnel)

  lazy val funnelriemann = Project("funnel-riemann", file("funnel-riemann"))
    .settings(buildSettings:_*)
    .settings(resolvers += "clojars.org" at "http://clojars.org/repo")
    .settings(libraryDependencies ++= 
      compile(riemann) ++ 
      compile(logback))
    .dependsOn(funnel)

  lazy val funnelcli = Project("funnel-cli", file("funnel-cli"))
    .settings(buildSettings:_*)
    .settings(crossPaths := false) // adding this because its an executable
    .dependsOn(funnel)
}
