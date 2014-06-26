import sbt._
import Keys._

object BuildSettings {
  val buildVersion = "1.0"
  val buildScalaVersion = "2.10.3"

  val buildSettings = Seq(
      version                  := buildVersion
    , scalaVersion             := buildScalaVersion
    , scalacOptions in Compile += "-deprecation" // output deprecation warnings
    , scalacOptions in Compile += "-Xexperimental" // for Mp4S
    , resolvers                := Seq( "local dotM2" at "file://"+Path.userHome.absolutePath+"/.m2/repository" )
    , externalResolvers        := Seq( "local dotM2" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
                                       "shanon artifactory repo" at System.getenv( "SBT_PROXY_REPO" ) + "/repo" )
    , credentials              += Credentials( System.getenv( "ARTIFACTORY_REALM" ),
                                               System.getenv( "ARTIFACTORY_HOST" ),
                                               System.getenv( "ARTIFACTORY_USER" ),
                                               System.getenv( "ARTIFACTORY_PASS" ) )
    , javaOptions              += "-Djsse.enableSNIExtension=false"

  )
}

object Dependencies {
  // for joda-time
  val jodaTime    = "joda-time" % "joda-time" % "2.3"
  val jodaConvert = "org.joda" % "joda-convert" % "1.6"

  // for config
  val typesafeConfig = "com.typesafe" % "config" % "0.6.0"

  // for akka
  val akkaVer = "2.3.3"
  val akkaActor  = "com.typesafe.akka"  %% "akka-actor" % akkaVer
  val akkaRemote = "com.typesafe.akka"  %% "akka-remote" % akkaVer
  val akkaSlfj   =  "com.typesafe.akka" %% "akka-slf4j" % akkaVer

  // PostgreSQL
  val scalikeJdbcAsync = "org.scalikejdbc" %% "scalikejdbc-async" % "0.4.0"

   // for MP
  val mp4s = "jp.co.shanon" %% "mp4s2" % "0.1.0-SNAPSHOT"

  // for test
  val scalatest   = "org.scalatest" %% "scalatest" % "2.1.6" % "test"
}

object MalvaBuild extends Build {
  import BuildSettings._
  import Dependencies._

  val commonDeps = Seq(
    scalatest
  )
  val managerDeps = Seq(
    jodaTime, jodaConvert,
    typesafeConfig,
    akkaActor, akkaRemote, akkaSlfj,
    scalikeJdbcAsync,
    scalatest
  )
  val workerDeps = Seq(
    jodaTime, jodaConvert,
    typesafeConfig,
    akkaActor, akkaRemote, akkaSlfj,
    mp4s,
    scalatest
  )

  lazy val malva = Project(
    "malva",
    file( "." ),
    settings = buildSettings
  ) aggregate( manager, worker )

  lazy val common = Project(
    "common",
    file( "common" ),
    settings = buildSettings ++ Seq(
      libraryDependencies ++= commonDeps
    )
  )

  lazy val manager = Project (
    "manager",
    file( "manager" ),
    settings = buildSettings ++ Seq(
      mainClass := Some( "Manager" )
      , libraryDependencies ++= managerDeps
    )
  ) dependsOn( common )

  lazy val worker = Project(
    "worker",
    file( "worker" ),
    settings = buildSettings ++ Seq(
      mainClass := Some( "Worker" )
      , libraryDependencies ++= workerDeps
    )
  ) dependsOn( common )
}
