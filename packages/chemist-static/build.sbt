
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

OnCue.baseSettings

Bundle.settings

name := "chemist-static"

name in Universal := name.value

mainClass in Compile := Some("funnel.chemist.static.Main")
