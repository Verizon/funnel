import sbt._
import Keys._
// plugins
import com.intel.media.MediaBuildPlugin

object IntelMediaBuild extends Build {

  val project_id = "commons-monitoring"

  object Dependencies {
      val deps = Seq(
          // Additional dependencies go here
         "reaktor" %% "scct" % "0.2.+" % "scct"
      )
  }

  object Resolvers {
      val resolver = Seq(
       // Additional resolvers go here
     )
  }

  lazy val buildSettings = Defaults.defaultSettings ++
      seq(ScctPlugin.instrumentSettings : _*) ++
      MediaBuildPlugin.MediaBuildSettings ++ Seq( // Custom plugin's settings specifics go here
      ) // end of buildSettings

  lazy val root = Project(   // Project's settings specifics go here
    id = project_id,
    base = file("."),
    settings = buildSettings ++ Seq(
      name := project_id,
      organization := "intelmedia.ws.commons-monitoring",  
      MediaBuildPlugin.mediabuildProjectid := project_id,
      libraryDependencies ++= Dependencies.deps,
      resolvers ++= Resolvers.resolver
    )
  )
}
