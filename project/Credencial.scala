package malba

import sbt.Keys._
import sbt._

object Credential {
  val realm = Option(System.getenv("ARTIFACTORY_REALM")).orElse(Option(System.getProperty("ARTIFACTORY_REALM"))).getOrElse(throw new Exception("You need to set `ARTIFACTORY_REALM`"))
  val host  = Option(System.getenv("ARTIFACTORY_HOST")).orElse(Option(System.getProperty("ARTIFACTORY_HOST"))).getOrElse(throw new Exception("You need to set `ARTIFACTORY_HOST`"))
  val user  = Option(System.getenv("ARTIFACTORY_USER")).orElse(Option(System.getProperty("ARTIFACTORY_USER"))).getOrElse(throw new Exception("You need to set `ARTIFACTORY_USER`"))
  val pass  = Option(System.getenv("ARTIFACTORY_PASS")).orElse(Option(System.getProperty("ARTIFACTORY_PASS"))).getOrElse(throw new Exception("You need to set `ARTIFACTORY_PASS`"))
  val repo  = Option(System.getenv("SBT_PROXY_REPO")).orElse(Option(System.getProperty("SBT_PROXY_REPO"))).getOrElse(throw new Exception("You need to set `SBT_PROXY_REPO`"))

  val settings = Seq(credentials += Credentials(realm, host, user, pass))
}
