
common.settings

libraryDependencies ++= Seq(
  "org.zeromq"       % "jeromq"               % "0.3.4",
  "org.scodec"      %% "scodec-core"          % V.scodec,
  "oncue.ermine"    %% "ermine-parser"        % V.ermine,
  "oncue.typelevel" %% "shapeless-scalacheck" % "0.4.0" % "test"
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "scala"

scalaSource in Test := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "test"
