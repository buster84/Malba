package malba

import sbt.Keys._
import sbt._
import sbt.Classpaths.publishTask

object Publish {
  lazy val MavenCompile = config("m2r") extend (Compile)
  lazy val publishLocalInMaven = TaskKey[Unit]("publish-local", "publish local for m2")

  lazy val settings = Credential.settings ++ Seq(
    otherResolvers := Seq(Resolver.file("dotM2", file(Path.userHome + "/.m2/repository")), Resolver.url("Shanon Maven Repo", new java.net.URL(Credential.repo + "/libs-snapshot-local"))),
    publishLocalConfiguration in MavenCompile <<= (packagedArtifacts, deliverLocal, ivyLoggingLevel) map {
      (arts, _, level) => new PublishConfiguration(None, "dotM2", arts, Seq(), level)
    },
    publishMavenStyle in MavenCompile := true,
    publishLocal in MavenCompile <<= publishTask(publishLocalConfiguration in MavenCompile, deliverLocal),
    publishLocalInMaven <<= Seq(publishLocal in MavenCompile).dependOn,
    publishTo := Some(Resolver.url("Shanon Maven Repo", new java.net.URL(Credential.repo + "/libs-snapshot-local")))
  )
}
