name := """play2.3"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "dotM2" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
)

libraryDependencies ++= Seq(
  "jp.co.shanon" %% "malba-client-playplugin" % "0.6-SNAPSHOT",
  jdbc,
  anorm,
  cache,
  ws
)
