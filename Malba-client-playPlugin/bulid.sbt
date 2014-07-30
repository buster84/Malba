import malba.{ Dependencies, MalbaBuild, Publish }

MalbaBuild.buildSettings

libraryDependencies ++= Dependencies.clientPlayPlugin

Publish.settings
