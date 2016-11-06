import com.typesafe.sbt.SbtScalariform.ScalariformKeys

import scalariform.formatter.preferences._

name := "akka-persistence-eventstore"

organization := "com.geteventstore"

scalaVersion := "2.11.8"

licenses := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.Akka.Persistence/master/LICENSE"))

homepage := Some(new URL("http://github.com/EventStore/EventStore.Akka.Persistence"))

organizationHomepage := Some(new URL("http://geteventstore.com"))

description := "Event Store Plugin for Akka Persistence"

startYear := Some(2013)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings",
  "-Xlint:-missing-interpolator",
  "-Yno-adapted-args",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Xfuture"
)

scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings")

resolvers += "spray" at "http://repo.spray.io/"

val AkkaVersion = "2.4.12"

lazy val IntegrationTest = config("it") extend Test

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-persistence-query-experimental" % AkkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
  "com.geteventstore" %% "eventstore-client" % "3.0.0",
  "org.specs2" %% "specs2-core" % "3.8.5.1" % Test,
  "org.json4s" %% "json4s-native" % "3.4.2" % Test,
  "io.spray" %%  "spray-json" % "1.3.2" % Test)

lazy val root = (project in file("."))
  .configs(IntegrationTest)
  .settings(Defaults.itSettings: _*)
  .settings(parallelExecution in IntegrationTest := false)

pomExtra in Global := {
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

SbtScalariform.scalariformSettings

ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignParameters, true)
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentClassDeclaration, true)