import oncue.build._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import NativePackagerKeys.scriptClasspath

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Bundle.settings

Revolver.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.compilation

normalizedName := "funnel-agent"

artifact in makePom := Artifact.pom("funnel-agent").copy(classifier = Some(name.value))

mappings in Universal ++= Seq(
  file("agent/deploy/etc/agent.cfg") -> "etc/agent.cfg"
)

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"  %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.svc.knobs" %% "core"                    % V.knobs,
  "io.netty"         % "netty-handler"           % V.netty,
  "io.netty"         % "netty-codec"             % V.netty,
  "com.github.cjmx" %% "cjmx"                    % "2.2.+" exclude("org.scala-sbt","completion") exclude("com.google.code.gson","gson")
)

mainClass in Revolver.reStart := Some("funnel.agent.Main")

javaOptions in Revolver.reStart += "-Xmx4g"

// Revolver.reStartArgs :=
//   ((sourceDirectory in Test).value / "resources/oncue/agent-jmx-kafka.cfg"
//     ).getCanonicalPath :: Nil

unmanagedClasspath in Compile ++= Custom.toolsJar

unmanagedClasspath in Test ++= Custom.toolsJar

// what follows is to support a use case where folks want to
// run the agent as a far jar, not with a startup script / bundle

jarName in assembly := s"${name.value}-${version.value}-standalone.jar"

mappings in Universal := {
  // universalMappings: Seq[(File,String)]
  val universalMappings = (mappings in Universal).value
  val fatJar = (assembly in Compile).value
  // removing means filtering
  val filtered = universalMappings filter {
      case (file, name) =>  ! name.endsWith(".jar")
  }
  filtered :+ (fatJar -> ("lib/" + fatJar.getName))
}

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

// the bash scripts classpath only needs the fat jar
scriptClasspath := Seq( (jarName in assembly).value )

addArtifact(Artifact("funnel-agent", "assembly"), assembly)
