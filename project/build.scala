import sbt._, Keys._

object Build extends Build {
  import Dependencies._

  val projectId = "commons-monitoring"

  lazy val buildSettings =
    Defaults.defaultSettings ++
    seq(ScctPlugin.instrumentSettings : _*)

  lazy val root = Project(projectId, file("."))
    .settings(buildSettings:_*)
    .settings(
      name := projectId,
      organization := "intelmedia.ws.commons",
      scalaVersion := "2.10.3")
    .settings(libraryDependencies ++=
      compile(scalaz) ++
      compile(scalazstream) ++
      compile(algebird) ++
      test(scalacheck) ++
      test(scalatest))
}
