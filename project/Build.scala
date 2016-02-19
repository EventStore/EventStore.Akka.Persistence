import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin._

object Build extends Build {
  lazy val basicSettings = Seq(
    name                 := "akka-persistence-eventstore",
    organization         := "com.geteventstore",
    scalaVersion         := "2.11.7",
    licenses             := Seq("BSD 3-Clause" -> url("http://raw.github.com/EventStore/EventStore.Akka.Persistence/master/LICENSE")),
    homepage             := Some(new URL("http://github.com/EventStore/EventStore.Akka.Persistence")),
    organizationHomepage := Some(new URL("http://geteventstore.com")),
    description          := "Event Store Plugin for Akka Persistence",
    startYear            := Some(2013),
    scalacOptions        := Seq("-encoding", "UTF-8", "-unchecked", "-deprecation", "-feature", "-Xlint"),
    resolvers            += "spray" at "http://repo.spray.io/",
    libraryDependencies ++= Seq(
      Akka.persistence, Akka.testkit, Akka.persistenceTck, Akka.persistenceQuery, Akka.streamTestkit,
      eventstore, specs2, json4s, sprayJson))

  object Akka {
    val persistence =      apply("akka-persistence")
    val persistenceTck =   apply("akka-persistence-tck") % "test"
    val persistenceQuery = apply("akka-persistence-query-experimental")
    val testkit =          apply("akka-testkit") % "test"

    val stream  =          apply("akka-stream")
    val streamTestkit =    apply("akka-stream-testkit") % "test"

    private def apply(x: String) = "com.typesafe.akka" %% x % "2.4.2"
  }

  val eventstore = "com.geteventstore" %% "eventstore-client" % "2.2.0"
  val specs2     = "org.specs2" %% "specs2-core" % "2.4.15" % "test"
  val json4s     = "org.json4s" %% "json4s-native" % "3.3.0"
  val sprayJson  = "io.spray" %% "spray-json" % "1.3.2" % "test"

  def integrationFilter(name: String): Boolean = name endsWith "IntegrationSpec"
  def specFilter(name: String): Boolean = (name endsWith "Spec") && !integrationFilter(name)

  lazy val IntegrationTest = config("it") extend Test

  lazy val root = Project(
    "akka-persistence-eventstore",
    file("."),
    settings = basicSettings ++ Defaults.coreDefaultSettings ++ releaseSettings ++ Scalariform.settings ++ Publish.settings)
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.testTasks): _*)
    .settings(
    testOptions       in Test            := Seq(Tests.Filter(specFilter)),
    testOptions       in IntegrationTest := Seq(Tests.Filter(integrationFilter)),
    parallelExecution in IntegrationTest := false)
}