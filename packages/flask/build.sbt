
import oncue.build._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.Keys._

OnCue.baseSettings

Bundle.settings

name in Universal := "flask"
