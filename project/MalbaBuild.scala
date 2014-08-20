package malba

import sbt.Keys._
import sbt._

object MalbaBuild extends Build {
  val appVersion = "0.6"

  val appName    = "Malba"

  // RPM Package settings
  val GROUPNAME  = appName.toLowerCase()
  val USERNAME   = appName.toLowerCase()

  val resolversList         = Seq( "local dotM2" at "file://"+Path.userHome.absolutePath+"/.m2/repository" )
  val externalResolversList = Seq( "local dotM2" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
                               "shanon artifactory repo" at Credential.repo + "/repo" )
  // val enableSNIExtension = Option(System.getProperty("jsse.enableSNIExtension")).getOrElse("true")

  val options = Seq( 
    "-encoding", "UTF-8",
    "-deprecation",         // warning and location for usages of deprecated APIs
    "-feature",             // warning and location for usages of features that should be imported explicitly
    "-unchecked",           // additional warnings where generated code depends on assumptions
    "-Xlint",               // recommended additional warnings
    "-Ywarn-adapted-args",  // Warn if an argument list is modified to match the receiver
    "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
    "-language:reflectiveCalls" )

  lazy val buildSettings = Credential.settings ++ Seq(
    organization              := "jp.co.shanon",
    version                   := appVersion,
    scalacOptions in Compile  := options,
    scalaVersion              := Dependencies.Versions.scalaVer,
    resolvers                 := resolversList,
    externalResolvers         := externalResolversList,
    parallelExecution in Test := false
  )

  lazy val root = Project(
    id        = "malba",
    base      = file("."),
    settings  = buildSettings,
    aggregate = Seq(master, protocol, client, clientPlayPlugin)
  )

  lazy val protocol = Project(
    id           = "malba-protocol",
    base         = file("Malba-protocol")
  )

  lazy val master = Project(
    id           = "malba-master",
    base         = file("Malba-master"),
    dependencies = Seq(protocol)
  )

  lazy val client = Project(
    id           = "malba-client",
    base         = file("Malba-client"),
    dependencies = Seq(protocol)
  )

  lazy val clientPlayPlugin = Project(
    id           = "malba-client-playPlugin",
    base         = file("Malba-client-playPlugin"),
    dependencies = Seq(client)
  )
}
