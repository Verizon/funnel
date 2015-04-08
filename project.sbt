
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
  messages,
  zeromq,
  agent,
  `zeromq-java`,
  `agent-windows`,
  flask,
  chemist,
  `chemist-aws`)

lazy val agent = project.dependsOn(zeromq % "test->test;compile->compile", http, nginx).configs(MultiJvm)

lazy val `agent-windows` = project.dependsOn(`zeromq-java`, http, nginx).configs(MultiJvm)

lazy val aws = project

lazy val chemist = project.dependsOn(core, http)

lazy val `chemist-aws` = project.dependsOn(chemist % "test->test;compile->compile", aws)

lazy val core = project

lazy val docs = project
//  .settings(unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(core))
  .dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val flask = project.dependsOn(aws % "test->test;compile->compile", riemann, elastic, messages, zeromq % "test->test;compile->compile")

lazy val http = project.dependsOn(core)

lazy val messages = project.dependsOn(core, zeromq).configs(MultiJvm)

lazy val nginx = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val zeromq = project.dependsOn(http).configs(MultiJvm)

lazy val `zeromq-java` = project.dependsOn(http).configs(MultiJvm)

OnCue.baseSettings

Publishing.ignore
