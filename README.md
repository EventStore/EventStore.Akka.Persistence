## Event Store Journal for Akka Persistence [![Build Status](https://travis-ci.org/EventStore/EventStore.Akka.Persistence.png?branch=master)](https://travis-ci.org/EventStore/EventStore.Akka.Persistence)

[Akka Persistence](http://doc.akka.io/docs/akka/2.3.2/scala/persistence.html) journal backed by [Event Store](http://geteventstore.com/).

To use this plugin prior default one, add the following to `application.conf`:

```akka.persistence.journal.plugin = eventstore.journal```

To configure EventStore.JVM client, see it's [reference.conf](https://github.com/EventStore/EventStore.JVM/blob/master/src/main/resources/reference.conf)

## Setup

* Maven:

Add to `pom.xml`

```xml
    <dependency>
        <groupId>com.geteventstore</groupId>
        <artifactId>akka-persistence-eventstore_2.11</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>
```

* Sbt

Add to `build.sbt`

```scala
    libraryDependencies += "com.geteventstore" %% "akka-persistence-eventstore" % "0.0.1-SNAPSHOT"
```
