
import oncue.build._

organization in Global  := "oncue.svc.funnel"

scalaVersion in Global  := "2.10.4"

lazy val funnel = project.in(file(".")).aggregate(
  core, http, elastic, riemann, aws, flask, chemist)

lazy val core = project

lazy val http = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val flask = project.dependsOn(core, http, riemann, elastic, aws)

lazy val chemist = project.dependsOn(http, aws)

lazy val aws = project.dependsOn(core)

lazy val zeromq = project.dependsOn(core)

OnCue.baseSettings

Publishing.ignore
