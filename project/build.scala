import sbt._
import Keys._
import com.intel.media.MediaBuildPlugin

object Build extends Build {

  val projectId = "commons-monitoring"

  lazy val buildSettings = 
    Defaults.defaultSettings ++
    seq(ScctPlugin.instrumentSettings : _*) ++
    MediaBuildPlugin.MediaBuildSettings

  lazy val root = Project(
    id = projectId,
    base = file("."),
    settings = buildSettings ++ Seq(
      name := projectId,
      organization := "intelmedia.ws.commons",  
      MediaBuildPlugin.mediabuildProjectid := projectId
    )
  )
}
