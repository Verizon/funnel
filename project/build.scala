import sbt._, Keys._

object Build extends Build {
  import Dependencies._

  val projectId = "commons-monitoring"

  lazy val buildSettings =
    Defaults.defaultSettings ++
    seq(ScctPlugin.instrumentSettings : _*)

  lazy val root = Project(projectId, file("."))
    .settings(buildSettings:_*)
    .settings(resolvers +=
      "pchiusano/Bintray" at "http://dl.bintray.com/pchiusano/maven")
    .settings(
      name := projectId,
      organization := "intelmedia.ws.commons",
      scalaVersion := "2.10.3")
    .settings(libraryDependencies ++=
      compile(scalaz) ++
      compile(scalazstream) ++
      compile(algebird) ++
      compile("pru" %% "pru" % "0.3") ++
      test(scalacheck) ++
      test(scalatest))
}
