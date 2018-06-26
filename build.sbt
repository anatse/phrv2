import sbt.Keys.libraryDependencies

name := """phrv2"""
organization := "PharmRus"
version := "1.0-SNAPSHOT"
maintainer := "Anatoly Sementsov <anatolse@gmail.com>"
packageSummary := "Pharmrus play2.6 server for pharmacy"
packageDescription := """Pharmrus play2.6 server for pharmacy"""
rpmVendor := "pharmrus"
rpmLicense := Some("Apache License")

val silhouetteVersion = "5.0.5"
val akkaVersion = "2.5.13"
val jacksonVersion = "2.+"

lazy val root = (project in file("."))
  .settings (
    antPackagerTasks in JDKPackager := Some(file("/usr/java/jdk1.8.0_172-amd64/lib/ant-javafx.jar")),
    name in Linux := name.value,
    scalaVersion := "2.12.6",
    resolvers ++= Seq (
      "Atlassian Releases" at "https://maven.atlassian.com/public/"
    ),
    PlayKeys.devSettings += "play.server.provider" -> "play.core.server.AkkaHttpServerProvider",
    libraryDependencies ++= Seq (
      guice,
      filters,
      logback,
      ehcache,

      // mongoDB
      "org.reactivemongo" %% "play2-reactivemongo" % "0.13.0-play26",

      /*
       * Authorization library silhouette
       */
      "com.mohiva" %% "play-silhouette" % silhouetteVersion exclude ("com.atlassian.jwt", "jwt-api") exclude ("com.atlassian.jwt", "jwt-core"),
      "com.atlassian.jwt" % "jwt-core" % "1.6.2",
      "com.atlassian.jwt" % "jwt-api" % "1.6.2",
      "com.mohiva" %% "play-silhouette-password-bcrypt" % silhouetteVersion,
      "com.mohiva" %% "play-silhouette-persistence" % silhouetteVersion,
      "com.mohiva" %% "play-silhouette-crypto-jca" % silhouetteVersion,

      "net.codingwell" %% "scala-guice" % "4.+",
      "com.iheart" %% "ficus" % "1.4.3",
      "com.nimbusds" % "nimbus-jose-jwt" % "5.11",

      /*
       * json library
       */
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,

      // HTML Parser
      "net.ruippeixotog" %% "scala-scraper" % "2.1.0",

      // Apache POI
      "org.apache.poi" % "poi" % "3.17",
      "org.apache.poi" % "poi-ooxml" % "3.17",

      // Akka stream
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,

      // Selenium for rosminzdrav
      "org.seleniumhq.selenium" % "selenium-java" % "3.12.0",

        /*
        * Test libraries
         */
      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "2.0.0" % Test,
      "com.mohiva" %% "play-silhouette-testkit" % silhouetteVersion % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      specs2 % Test
    ),
    jdkPackagerJVMArgs := Seq(
      "-Dhttp.port=disabled",
      "-Dhttps.port=9443",
      "-Djdk.tls.ephemeralDHKeySize=2048",
      "-Djdk.tls.rejectClientInitiatedRenegotiation=true",
      "-Djava.security.properties=disabledAlgorithms.properties",
      "-Dcom.sun.net.ssl.rsaPreMasterSecretFix=true",
      "-Dsun.security.ssl.allowUnsafeRenegotiation=false",
      "-Dsun.security.ssl.allowLegacyHelloMessages=false"
    ),
    javaOptions ++= Seq(
      "-Dhttp.port=disabled",
      "-Dhttps.port=9443",
      "-Djdk.tls.ephemeralDHKeySize=2048",
      "-Djdk.tls.rejectClientInitiatedRenegotiation=true",
      "-Djava.security.properties=disabledAlgorithms.properties",
      "-Dcom.sun.net.ssl.rsaPreMasterSecretFix=true",
      "-Dsun.security.ssl.allowUnsafeRenegotiation=false",
      "-Dsun.security.ssl.allowLegacyHelloMessages=false"
    ),
    scalacOptions ++= Seq(
      "-unchecked",
      "-deprecation",
      "-target:jvm-1.8"
    )
  )
  .enablePlugins(PlayScala, PlayAkkaHttp2Support, JavaAppPackaging, LinuxPlugin, RpmPlugin, JDKPackagerPlugin)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "org.asem.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "org.asem.binders._"
