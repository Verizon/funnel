
import sbt._, Keys._
import oncue.build.{Compilation, Versioning}
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.tools.mima.plugin.MimaPlugin.mimaDefaultSettings
import com.typesafe.tools.mima.plugin.MimaKeys.previousArtifact
import spray.revolver.RevolverPlugin._

object Custom {

  val compilation = Seq(
    scalacOptions := Compilation.flags.filterNot(_ == "-Xlint"))

  val resources =
    unmanagedResourceDirectories in Test <+= baseDirectory(_ / ".." / "etc" / "classpath" / "test")

  val revolver = Seq(
    javaOptions in Revolver.reStart += s"-Dlogback.configurationFile=${baseDirectory.value}/../etc/classpath/logback.xml",
    Revolver.reStartArgs :=
      (baseDirectory.value / ".." / "etc" / "development" / name.value / s"${name.value}.dev.cfg").getCanonicalPath :: Nil
  )

  val testing = Seq(
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    }
  )

  val toolsJar = {
    val javaHome = Path(Path.fileProperty("java.home").asFile.getParent)
    val toolsJar = Option(javaHome / "lib" / "tools.jar").filter { _.exists }
    if (toolsJar.isEmpty && !appleJdk6OrPrior) sys.error("tools.jar not in $JAVA_HOME/lib")
    toolsJar.toSeq
  }

  def appleJdk6OrPrior: Boolean = {
    (sys.props("java.vendor") contains "Apple") && {
      val JavaVersion = """^(\d+)\.(\d+)\..*$""".r
      val JavaVersion(major, minor) = sys.props("java.version")
      major.toInt == 1 && minor.toInt < 7
    }
  }

  val binaryCompatibility: Seq[Setting[_]] =
    mimaDefaultSettings ++
    Seq(
      previousArtifact := Some(
        "oncue.svc.funnel" %%
        name.value %
        Versioning.buildNumber(micro = Some("+")).getOrElse(sys.error(s"invalid version number in version.sbt"))))
}
