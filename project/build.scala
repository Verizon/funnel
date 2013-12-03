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
  ).aggregate(spout, funnel)

  lazy val spout = Project("spout", file("spout"))
    .settings(buildSettings:_*)
    .settings(name := "spout")
    .settings(libraryDependencies ++=
      compile(scalaz) ++
      compile(scalazstream) ++
      compile(algebird) ++
      compile(argonaut) ++
      test(scalacheck) ++
      test(scalatest))

  lazy val funnel = Project("funnel", file("funnel"))
    .settings(buildSettings:_*)
    .settings(crossPaths := false) // adding this because its an executable
    .settings(libraryDependencies ++= compile(scopt))
    .dependsOn(spout)
}
