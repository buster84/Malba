import malba.{ Dependencies, MalbaBuild, Publish }

MalbaBuild.buildSettings

version := MalbaBuild.appVersion + "-SNAPSHOT"

libraryDependencies ++= Dependencies.client

Publish.settings
