
import oncue.build._

organization in Global  := "intelmedia.ws.funnel"

scalaVersion in Global  := "2.10.4"

lazy val funnel = project.in(file(".")).aggregate(core, http, riemann, aws, flask, chemist)

lazy val core = project

lazy val http = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val flask = project.dependsOn(core, http, riemann)

lazy val chemist = project.dependsOn(http)

lazy val aws = project.dependsOn(core)

OnCue.baseSettings

Publishing.ignore
