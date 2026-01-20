# 0. Release Notes (2.0.x)

## 2.0.0-M1

This is milestone release and is aimed at testing this new major version
by early adopters. This is experimental. This release should not be used in production.

### Main changes

* Pekko 2.0.0-M1 is the new minimum Pekko version
* Java 17 is the new minimum JRE version
* Scala 2.12 support dropped
* A lot of deprecated code has been removed
* A lot of util classes for Scala version compatibility have been removed
* Dependency versions have been updated
* Jackson3 is supported in a new pekko-http-jackson3 lib
* Changed Java DSL methods that return Scala Durations to return Java Durations ([PR788](https://github.com/apache/pekko-http/pull/788))
