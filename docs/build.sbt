import com.typesafe.sbt.SbtSite.SiteKeys._
import com.typesafe.sbt.SbtGhPages.GhPagesKeys._
//import sbtunidoc.Plugin.UnidocKeys._
import oncue.build._

site.settings

tutSettings

site.addMappingsToSiteDir(tut, "tut")

OnCue.baseSettings

Publishing.ignore

//unidocSettings

//site.addMappingsToSiteDir(mappings in (ScalaUnidoc, packageDoc), "api")

ghpages.settings

ghpagesNoJekyll := false

includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.gif" | "*.js" | "*.swf" | "*.yml" | "*.md"

git.remoteRepo := "git@github.oncue.verizon.net:IntelMedia/funnel.git"

/*
scalacOptions in (ScalaUnidoc, sbtunidoc.Plugin.UnidocKeys.unidoc) ++= Seq(
  "-language:postfixOps",
  "-doc-source-url", "https://github.oncue.verizon.net/Intelmedia/funnel/blob/master€{FILE_PATH}.scala",
  "-sourcepath", baseDirectory.in(LocalRootProject).value.getAbsolutePath
)
 */
