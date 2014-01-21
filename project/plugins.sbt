
resolvers ++= Seq(
  "Sonatype Nexus Central Repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/central/",
  "Sonatype Nexus Typesafe Repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/typesafe/",
  "Sonatype Nexus Sonatype Repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/sonatype/",
  "Sonatype Nexus Artifactoryonline Repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/artifactoryonline/",
  "Sonatype Nexus Releases Repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/releases/",
  "Sonatype Nexus 3rd party repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/thirdparty/",
  "Sonatype Nexus Spray repository" at "http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/spray/",
  Resolver.url("Sonatype Nexus Artifactoryonline Scalasbt Repository", url("http://nexus-nexusloadbal-1cj6r4t1cb574-1345959472.us-east-1.elb.amazonaws.com/nexus/content/repositories/artifactoryonline-scalasbt/"))(Resolver.ivyStylePatterns)
)

addSbtPlugin("intelmedia.build" %% "sbt-imbuild" % "4.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.0-M1")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
