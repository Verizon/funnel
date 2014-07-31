
import oncue.build._

organization in Global  := "intelmedia.ws.funnel"

scalaVersion in Global  := "2.10.4"

lazy val funnel = project.in(file(".")).aggregate(core, http, riemann, utensil)

lazy val core = project

lazy val http = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val sink = project.dependsOn(core, http, riemann)

OnCue.baseSettings

Publishing.ignore
