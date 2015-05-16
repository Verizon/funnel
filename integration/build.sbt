import oncue.build._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Bundle.settings

Revolver.settings

SbtMultiJvm.multiJvmSettings

Custom.testing

Custom.compilation

normalizedName := "integration"

artifact in makePom := Artifact.pom("integration").copy(classifier = Some(name.value))

