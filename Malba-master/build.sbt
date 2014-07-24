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

rpmRequirements    ++= Seq("shadow-utils")

rpmUrl             := Some("https://ghe01.shanon.co.jp/Shanon/Malba")

rpmLicense         := Some("""Commercial""")

rpmPre             := Some(s"""|getent group ${MalbaBuild.GROUPNAME} >/dev/null || groupadd -r ${MalbaBuild.GROUPNAME}
                               |getent passwd ${MalbaBuild.USERNAME} >/dev/null || \\
                               |  useradd -r -g ${MalbaBuild.GROUPNAME} -d /home/malba -s /sbin/nologin \\
                               |  -c "To start this malba application" ${MalbaBuild.USERNAME}
                               |mkdir -p /var/log/malba-master && chown ${MalbaBuild.USERNAME}:${MalbaBuild.GROUPNAME} -R /var/log/malba-master
                               |mkdir -p /mnt/${MalbaBuild.USERNAME} && chown ${MalbaBuild.USERNAME}:${MalbaBuild.GROUPNAME} /mnt/${MalbaBuild.USERNAME}
                               |exit 0
                               |""".stripMargin)

rpmPost            := Some("""echo "Post install" """)

rpmPreun           := Some("""echo "Pre uninstall" """)

rpmPostun          := Some("""echo "Post uninstall" """)
