
common.settings

common.revolver

libraryDependencies += "oncue.knobs" %% "core" % V.knobs

fork in Test := true

mainClass in run := Some("funnel.flask.Main")
