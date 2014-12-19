
import oncue.build._

import com.typesafe.sbt.SbtMultiJvm

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

OnCue.baseSettings

ScalaCheck.settings

ScalaTest.settings

SbtMultiJvm.multiJvmSettings

libraryDependencies += "org.zeromq" % "jeromq" % "0.3.4"

// make sure that MultiJvm test are compiled by the default test compilation
compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test)

// disable parallel tests
parallelExecution in Test := false

// make sure that MultiJvm tests are executed by the default test target,
// and combine the results from ordinary test and multi-jvm tests
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
