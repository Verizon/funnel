import oncue.build._
import spray.revolver.RevolverPlugin._

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Revolver.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.compilation

Custom.revolver

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"  %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.knobs"     %% "core"                    % V.knobs,
  "io.netty"         % "netty-handler"           % V.netty,
  "io.netty"         % "netty-codec"             % V.netty,
  "com.github.cjmx" %% "cjmx"                    % "2.2.+" exclude("org.scala-sbt","completion") exclude("com.google.code.gson","gson")
)

mainClass in Revolver.reStart := Some("funnel.agent.Main")

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

unmanagedClasspath in Compile ++= Custom.toolsJar

unmanagedClasspath in Test ++= Custom.toolsJar
