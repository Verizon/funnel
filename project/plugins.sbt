
resolvers += "im.nexus" at "http://nexus.svc.m.infra-host.com/nexus/content/groups/intel_media_maven/"

addSbtPlugin("intelmedia.build" %% "sbt-imbuild" % "5.1.+")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.0-M1")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
