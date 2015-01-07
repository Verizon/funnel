
import oncue.build._
import spray.revolver.RevolverPlugin._
import com.typesafe.sbt.SbtMultiJvm
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

Revolver.settings

SbtMultiJvm.multiJvmSettings

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-filter"       % "0.8.3",
  "net.databinder" %% "unfiltered-netty-server" % "0.8.3"
)

mainClass in Revolver.reStart := Some("oncue.svc.funnel.agent.Main")

compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)

parallelExecution in Test := false

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
