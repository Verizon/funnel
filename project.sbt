
import oncue.build._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

Publishing.ignore

organization in Global  := "oncue.svc.funnel"

scalaVersion in Global  := "2.10.5"

lazy val funnel = project.in(file(".")).aggregate(
  core,
  http,
  elastic,
  nginx,
  riemann,
  integration,
  telemetry,
  zeromq,
  agent,
  `zeromq-java`,
  flask,
  chemist,
  `chemist-aws`,
  `chemist-static`,
  `agent-package`,
  `agent-windows-package`,
  `flask-package`,
  `chemist-aws-package`,
  `chemist-static-package`
)

lazy val agent = project.dependsOn(zeromq % "test->test;compile->compile", http, nginx).configs(MultiJvm)

lazy val chemist = project.dependsOn(core, http, telemetry)

lazy val `chemist-aws` = project.dependsOn(chemist % "test->test;compile->compile")

lazy val `chemist-static` = project.dependsOn(chemist % "test->test;compile->compile")

lazy val core = project

lazy val docs = project.dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val flask = project.dependsOn(riemann, elastic, telemetry, zeromq % "test->test;compile->compile")

lazy val http = project.dependsOn(core)

lazy val integration = project.dependsOn(flask, chemist % "test->test;compile->compile").configs(MultiJvm)

lazy val nginx = project.dependsOn(core)

lazy val riemann = project.dependsOn(core)

lazy val telemetry = project.dependsOn(zeromq).configs(MultiJvm)

lazy val zeromq = project.dependsOn(core, http).configs(MultiJvm) // http? this is for http.JSON._, which should be fixed probably

lazy val `zeromq-java` = project.dependsOn(http).configs(MultiJvm)

//////////////////////////// packages for service deployables ////////////////////////////

lazy val `agent-package` = project.in(
  file("packages/agent")).dependsOn(agent)

lazy val `agent-windows-package` = project.in(
  file("packages/agent-windows")).dependsOn(`zeromq-java`, http, nginx).configs(MultiJvm)

lazy val `flask-package` = project.in(
  file("packages/flask")).dependsOn(flask)

lazy val `chemist-aws-package` = project.in(
  file("packages/chemist-aws")).dependsOn(`chemist-aws`)

lazy val `chemist-static-package` = project.in(
  file("packages/chemist-static")).dependsOn(`chemist-static`)
