> [!WARNING]
> **DEPRECATION NOTICE**: 
> * EventStoreDB version 23.10.x is the last OSS version to support the tcp protocol based client.
> * This project is no longer maintained. 
> We recommend moving to [EventStoreDB-Client-Java](https://github.com/EventStore/EventStoreDB-Client-Java) for ongoing updates and support.

### Event Store Plugin for Akka Persistence [![Continuous Integration](https://github.com/EventStore/EventStore.Akka.Persistence/actions/workflows/ci.yml/badge.svg)](https://github.com/EventStore/EventStore.Akka.Persistence/actions/workflows/ci.yml) [![Version](https://img.shields.io/maven-central/v/com.geteventstore/akka-persistence-eventstore_2.13.svg?label=version)](http://search.maven.org/#search%7Cga%7C1%7Cg%3Acom.geteventstore%20AND%20akka-persistence-eventstore)

[Akka Persistence](https://doc.akka.io/docs/akka/current/persistence.html) journal and snapshot-store backed by [EventStoreDB](https://eventstore.com).

<table border="0">
  <tr>
    <td><a href="http://www.scala-lang.org">Scala</a> </td>
    <td>3.1.0 / 2.13.7 / 2.12.15</td>
  </tr>
  <tr>
    <td><a href="http://akka.io">Akka</a> </td>
    <td>2.6.18</td>
  </tr>
  <tr>
    <td><a href="https://github.com/EventStore/EventStore.JVM">EventStore client</a> </td>
    <td>8.0.1</td>
  </tr>
</table>

To use this plugin prior default one, add the following to `application.conf`:

```
akka.persistence {
  journal.plugin = eventstore.persistence.journal
  snapshot-store.plugin = eventstore.persistence.snapshot-store
}
```

To configure EventStore.JVM client, see it's [reference.conf](https://github.com/EventStore/EventStore.JVM/blob/master/src/main/resources/reference.conf)

### JSON serialization

Akka serializes your messages into binary data by default.
However you can [add your own serializer](https://doc.akka.io/docs/akka/current/serialization.html#Customization) to serialize as JSON,
But make sure you extend `akka.persistence.eventstore.EventStoreSerializer` rather then `akka.serialization.Serializer`. 
And in case you are really going to serialize as json, please specify `ContentType.Json`, it will allow you to use projections.
 
```scala
trait EventStoreSerializer extends Serializer {
  def toEvent(o: AnyRef): EventData
  def fromEvent(event: Event, manifest: Class[_]): AnyRef
}
```
 
Please check out some real examples used in tests:
* [json4s](https://github.com/EventStore/EventStore.Akka.Persistence/blob/master/src/it/scala/akka/persistence/eventstore/Json4sSerializer.scala)
* [spray-json](https://github.com/EventStore/EventStore.Akka.Persistence/blob/master/src/it/scala/akka/persistence/eventstore/SprayJsonSerializer.scala)


## Setup

#### Sbt
```scala
libraryDependencies += "com.geteventstore" %% "akka-persistence-eventstore" % "8.0.1"
```

#### Maven
```xml
<dependency>
    <groupId>com.geteventstore</groupId>
    <artifactId>akka-persistence-eventstore_${scala.version}</artifactId>
    <version>8.0.0</version>
</dependency>
```
