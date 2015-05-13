
import oncue.build._

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
//import sbtunidoc.Plugin.UnidocKeys._

organization in Global  := "oncue.svc.funnel"

scalaVersion in Global  := "2.10.4"

lazy val funnel = project.in(file(".")).aggregate(
  core,
  http,
  elastic,
  nginx,
  riemann,
  telemetry,
  zeromq,
  agent,
  `zeromq-java`,
  `agent-windows`,
  flask,
  chemist,
  `chemist-aws`,
  `chemist-static`)

lazy val agent = project.dependsOn(zeromq % "test->test;compile->compile", http, nginx).configs(MultiJvm)

lazy val `agent-windows` = project.dependsOn(`zeromq-java`, http, nginx).configs(MultiJvm)

lazy val chemist = project.dependsOn(core, http)

lazy val `chemist-aws` = project.dependsOn(chemist % "test->test;compile->compile", telemetry)

lazy val `chemist-static` = project.dependsOn(chemist % "test->test;compile->compile")

lazy val core = project

lazy val docs = project
//  .settings(unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core))
  .dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val flask = project.dependsOn(riemann, elastic, telemetry, zeromq % "test->test;compile->compile")

lazy val http = project.dependsOn(core)

lazy val telemetry = project.dependsOn(zeromq).configs(MultiJvm)

lazy val nginx = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val zeromq = project.dependsOn(core, http).configs(MultiJvm) // http? this is for http.JSON._, which should be fixed probably

lazy val `zeromq-java` = project.dependsOn(http).configs(MultiJvm)

OnCue.baseSettings

Publishing.ignore
