import malba.{ Dependencies, MalbaBuild, Publish }

libraryDependencies ++= Dependencies.protocol

MalbaBuild.buildSettings

version := MalbaBuild.appVersion + "-SNAPSHOT"

Publish.settings
