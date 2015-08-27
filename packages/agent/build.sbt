import oncue.build._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import NativePackagerKeys.scriptClasspath
import com.typesafe.sbt.SbtNativePackager._

OnCue.baseSettings

Bundle.settings ++ Seq(
  publish <<= publish dependsOn Def.taskDyn {
                           (packageBin in Debian)
                       }
)

normalizedName := "funnel-agent"

mainClass in Compile := Some("funnel.agent.Main")

artifact in makePom := Artifact.pom("funnel-agent").copy(classifier = Some(name.value))

mappings in Universal ++= Seq(
  file("packages/agent/deploy/etc/agent.cfg") -> "etc/agent.cfg"
)

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
