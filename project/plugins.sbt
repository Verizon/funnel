resolvers += Resolver.url(
  "tpolecat-sbt-plugin-releases",
    url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers += "im.nexus" at "http://nexus.svc.oncue.com/nexus/content/groups/intel_media_maven/"

addSbtPlugin("oncue.build" %% "sbt-oncue" % "7.2.+")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

// has a transitive dependency on sbt-assembly 0.13.0
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.11")

addSbtPlugin("com.typesafe.sbt" % "sbt-site"    % "0.8.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.3")

addSbtPlugin("com.eed3si9n"     % "sbt-unidoc"  % "0.3.2")

addSbtPlugin("org.tpolecat"     % "tut-plugin"  % "0.3.1")
