// Comment to get more information during initialization
logLevel := Level.Warn

// The Typesafe repository 
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

externalResolvers ++= Seq("maven proxy repo" at System.getenv("SBT_PROXY_REPO") + "/repo", 
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository", 
  "dotM2" at "file://"+Path.userHome.absolutePath+"/.ivy2/local")

credentials += Credentials(System.getenv("ARTIFACTORY_REALM"), System.getenv("ARTIFACTORY_HOST"), System.getenv("ARTIFACTORY_USER"), System.getenv("ARTIFACTORY_PASS"))

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "0.99.5.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.7.3")

// EclipseKeys.skipParents in ThisBuild := false
