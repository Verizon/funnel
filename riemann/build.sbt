
common.settings

resolvers += "clojars" at "http://clojars.org/repo"

libraryDependencies +=
  "com.aphyr" % "riemann-java-client" % "0.4.1" exclude("com.codahale.metrics","metrics-core")

scalacOptions += "-language:postfixOps"
