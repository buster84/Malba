package malba

import sbt.Keys._
import sbt._

object Dependencies {
  object Versions {
    val scalaVer = "2.10.4"
    val akkaVer  = "2.3.5"
  }

  import Versions._
  // Akka
  val akkaActor       = "com.typesafe.akka" %% "akka-actor"   % akkaVer
  val akkaRemote      = "com.typesafe.akka" %% "akka-remote"  % akkaVer
  val akkaSlfj        = "com.typesafe.akka" %% "akka-slf4j"   % akkaVer
  val akkaContrib     = "com.typesafe.akka" %% "akka-contrib" % akkaVer
  val akkaCluster     = "com.typesafe.akka" %% "akka-cluster" % akkaVer

  // Akka persistence 
  val akkaPersistence      = "com.typesafe.akka"   %% "akka-persistence-experimental" % akkaVer
  val akkaPersistenceMongo = "com.github.ddevore"  %% "akka-persistence-mongo-casbah" % "0.7.3-SNAPSHOT" % "compile"
  val akkaPersistenceJDBC  = "com.github.dnvriend" %% "akka-persistence-jdbc"         % "1.0.0"
  val postgresJDBC         = "org.postgresql"      % "postgresql"                     % "9.3-1101-jdbc41"

  // Logger
  val logback  = "ch.qos.logback" % "logback-classic" % "1.0.13"
  val mail     = "javax.mail" % "mail" % "1.4"

  // joda time
  val jodatime    = "joda-time" % "joda-time" % "2.3"
  val jodaconvert = "org.joda" % "joda-convert" % "1.2"

  // For test
  object Test {
    val akkaTestKit = "com.typesafe.akka" %% "akka-testkit" % akkaVer   % "test"
    val h2JDBC      = "com.h2database"    %  "h2"           % "1.4.180" % "test"
    val scalaTest   = "org.scalatest"     %% "scalatest"    % "2.1.6"   % "test"
    val commonIO    = "commons-io"        %  "commons-io"   % "2.4"     % "test"
  }

  val master = Seq(
    akkaActor, akkaRemote, akkaCluster, akkaContrib, akkaSlfj, akkaPersistence, akkaPersistenceMongo, logback, mail,
    Test.akkaTestKit, Test.h2JDBC, Test.scalaTest, Test.commonIO
  )

  val client = Seq(
    akkaActor, akkaRemote, akkaSlfj, logback, mail, jodatime, jodaconvert,
    Test.akkaTestKit, Test.scalaTest, Test.commonIO
  )

  val protocol = Seq(
    "com.typesafe.akka" %% "akka-actor"   % "2.2.4",
    jodatime, jodaconvert
  )

  val clientPlayPlugin = Seq(
    "com.typesafe.play" %% "play" % "2.3.0" % "provided" cross CrossVersion.binary,
    "com.typesafe.play" %% "play-test" % "2.3.0" % "test" cross CrossVersion.binary,
    "org.specs2" % "specs2" % "2.3.12" % "test" cross CrossVersion.binary
  )

}
