
common.settings

resolvers += Resolver.bintrayRepo("oncue", "releases")

// Custom.binaryCompatibility

// pure-java implementation:
// libraryDependencies += "org.zeromq" % "jeromq" % "0.3.4"
// native c++ implementation with jni:
libraryDependencies ++= Seq(
  "org.zeromq"       % "jzmq"                 % "3.1.0",
  "org.scodec"      %% "scodec-core"          % V.scodec,
  "oncue.ermine"    %% "ermine-parser"        % V.ermine //,
  // "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test"
)
