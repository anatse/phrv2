import sbt.Keys.libraryDependencies

name := """phrv2"""
organization := "org.asem"

version := "1.0-SNAPSHOT"

val silhouetteVersion = "5.0.5"
val akkaVersion = "2.5.13"
val jacksonVersion = "2.+"

lazy val root = (project in file("."))
  .settings (
    scalaVersion := "2.12.6",
    resolvers ++= Seq (
      "Atlassian Releases" at "https://maven.atlassian.com/public/"
    ),
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

      "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "2.0.0" % Test,
      "com.mohiva" %% "play-silhouette-testkit" % silhouetteVersion % "test",
      "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
      specs2 % Test
    )
  )
  .enablePlugins(PlayScala, PlayAkkaHttp2Support, JavaAppPackaging)


// Adds additional packages into Twirl
//TwirlKeys.templateImports += "org.asem.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "org.asem.binders._"
