lazy val isScala3 = Def.setting(CrossVersion.partialVersion(scalaVersion.value).exists(_._1 == 3))

name := "akka-persistence-eventstore"

organization := "com.geteventstore"

scalaVersion := crossScalaVersions.value.last

crossScalaVersions := Seq("2.12.15", "2.13.7", "3.1.0")

releaseCrossBuild := true

licenses := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.Akka.Persistence/master/LICENSE"))

homepage := Some(new URL("http://github.com/EventStore/EventStore.Akka.Persistence"))

organizationHomepage := Some(new URL("http://geteventstore.com"))

description := "Event Store Plugin for Akka Persistence"

startYear := Some(2013)

scalacOptions ++= { if (isScala3.value) Seq("-Xtarget:8") else Seq("-target:jvm-1.8") }
javacOptions  ++= Seq("-target", "8", "-source", "8")
scalacOptions --= Seq("-Ywarn-unused:params", "-Wunused:params", "-Wunused:explicits", "-Wunused:nowarn")
Compile / doc / scalacOptions ++= Seq("-groups", "-implicits", "-no-link-warnings")

///

val ClientVersion = "8.0.1"
val CirceVersion = "0.14.1"
val AkkaVersion = "2.6.18"

lazy val IntegrationTest = config("it") extend Test

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.geteventstore" %% "eventstore-client" % ClientVersion,
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
   // akka-persistence-query relies on akka-remote via QuerySerializer, however it uses "provided".
  "com.typesafe.akka" %% "akka-remote" % AkkaVersion % Test,
  ("org.specs2" %% "specs2-core" % "4.13.1").cross(CrossVersion.for3Use2_13) % Test,
  "org.json4s" %% "json4s-native" % "4.0.3" % Test,
  "io.spray" %% "spray-json" % "1.3.6" % Test
)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(IntegrationTest / parallelExecution := false)

Global / pomExtra := {
  <scm>
    <url>git@github.com:EventStore/EventStore.Akka.Persistence.git</url>
    <connection>scm:git:git@github.com:EventStore/EventStore.Akka.Persistence.git</connection>
    <developerConnection>scm:git:git@github.com:EventStore/EventStore.Akka.Persistence.git</developerConnection>
  </scm>
    <developers>
      <developer>
        <id>t3hnar</id>
        <name>Yaroslav Klymko</name>
        <email>t3hnar@gmail.com</email>
      </developer>
    </developers>
}

releasePublishArtifactsAction := PgpKeys.publishSigned.value

publishTo := sonatypePublishTo.value
