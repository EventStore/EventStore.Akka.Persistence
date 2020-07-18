# Changelog


This change log is ordered chronologically, so each release contains all changes described below
it. Changes start from v6.0.0 and only relevant or interesting changes are mentioned. 

## v7.2.0 (2020-07-10)

### Dependencies

- Upgrades
  * eventstore-client: 7.1.0 -> 7.2.0
  * akka-*: 2.6.1 -> 2.6.8
  * json4s-native: 3.6.7 -> 3.6.9
  * specs2-core: 4.8.3 -> 4.10.0

## v7.0.1 (2019-08-22)

### Dependencies

- Upgrades
  * eventstore-client: 7.0.1 -> 7.0.2
  * akka-*: 2.5.23 -> 2.5.25
  * json4s-native: 3.6.6 -> 3.6.7
  * specs2-core: 4.5.1 -> 4.7.0

## v7.0.0 (2019-06-18)

### Breaking changes

* [#47](https://github.com/EventStore/EventStore.Akka.Persistence/pull/47): 

  - Drop Scala 2.11 support.
  - Upgrade `eventstore-client` to v7.0.1, which has breaking changes. See its [changelog](https://github.com/EventStore/EventStore.JVM/blob/master/CHANGELOG.md) for details.


### Enhancements

* [#47](https://github.com/EventStore/EventStore.Akka.Persistence/pull/47): Add Scala 2.13.0 support.

### Internals

* [#47](https://github.com/EventStore/EventStore.Akka.Persistence/pull/47):

  - Change `asyncWriteMessages` in `EventStoreJournal` to use `List` instead of `Traversable` as it is deprecated in Scala 2.13.0 and causes compile error. The deprecation warning suggests to use `Iterable` instead, but that causes stack
overflow in "may serialize events" test in `JournalPerfIntegrationSpec` in Scala 2.12.8. Using `List` solves the issue.
  - Adjust travis CI docker setup to use in-memory db and change stats period in order to avoid interference with tests. Moreover, 
    Integration tests use Scala 2.13.0 now, but we still compile code and tests using both Scala 2.12.8 and 2.13.0.

### Dependencies

- Upgrades

  * eventstore-client: 6.0.0 -> 7.0.1
  * akka-*: 2.5.21 -> 2.5.23
  * json4s-native: 3.6.5 -> 3.6.6
  * specs2-core: 4.4.1 -> 4.5.1
