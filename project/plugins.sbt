
resolvers += "im.nexus" at "http://nexus.svc.oncue.com/nexus/content/groups/intel_media_maven/"

addSbtPlugin("oncue.build" %% "sbt-oncue" % "6.5.+")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
