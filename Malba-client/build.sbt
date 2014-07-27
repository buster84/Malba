import malba.{ Dependencies, MalbaBuild, Publish }

MalbaBuild.buildSettings

libraryDependencies ++= Dependencies.client

Publish.settings
