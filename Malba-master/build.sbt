import malba.{ Dependencies, MalbaBuild }
import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

MalbaBuild.buildSettings

libraryDependencies ++= Dependencies.master

// Setup the packager
packageArchetype.java_server

daemonUser in Linux  := MalbaBuild.USERNAME  // user which will execute the application

daemonGroup in Linux := MalbaBuild.GROUPNAME // group which will execute the application

// Enable JAR export for staging
exportJars := true

maintainer in Linux     := "Yasuki Okumura <okumura.y@shanon.co.jp>"

packageSummary in Linux := "Job load balancing. Support a bunch of custom queuing."

packageDescription      := """Job load balancing. Support a bunch of custom queuing."""

rpmRelease         := Option(System.getenv("BUILD_NUMBER")).getOrElse(new java.util.Date().getTime().toString)

rpmVendor          := "Shanon"

rpmUrl             := Some("https://ghe01.shanon.co.jp/Shanon/Malba")

rpmLicense         := Some("""Commercial""")
