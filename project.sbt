
import oncue.build._

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

organization in Global  := "oncue.svc.funnel"

scalaVersion in Global  := "2.10.4"

lazy val funnel = project.in(file(".")).aggregate(core, http, elastic, riemann, zeromq, agent)

lazy val core = project

lazy val http = project.dependsOn(core)

lazy val flask = project.dependsOn(aws, riemann, elastic)

lazy val riemann = project.dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val zeromq = project.dependsOn(http).configs(MultiJvm)

lazy val agent = project.dependsOn(zeromq % "test->test;compile->compile", http).configs(MultiJvm)

lazy val chemist = project.dependsOn(core, http, aws)

lazy val aws = project

lazy val chemist = project.dependsOn(core, http, aws)

lazy val aws = project

OnCue.baseSettings

Publishing.ignore
