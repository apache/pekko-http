# 0. Release Notes (1.0.x)

## 1.0.1

This is a bug fix release. There have also been some doc and build improvements.

### Bug Fixes
* Add optional throttle to handle excessive HTTP/2 non-data frames ([PR394](https://github.com/apache/pekko-http/pull/394))
* Set the minimum required Pekko version to 1.0.0 (had been 1.0.1) ([PR328](https://github.com/apache/pekko-http/pull/328))

## 1.0.0

Apache Pekko HTTP 1.0.0 is based on Akka HTTP 10.2.10. Pekko came about as a result of Lightbend's decision to make future
Akka releases under a [Business Software License](https://www.lightbend.com/blog/why-we-are-changing-the-license-for-akka),
a license that is not compatible with Open Source usage.

Apache Pekko has changed the package names, among other changes. Config names have changed to use `pekko` instead
of `akka` in their names. Users switching from Akka to Pekko should read our @ref:[Migration Guide](../migration-guide/index.md).

Generally, we have tried to make it as easy as possible to switch existing Akka based projects over to using Pekko.

We have gone through the code base and have tried to properly acknowledge all third party source code in the
Apache Pekko code base. If anyone believes that there are any instances of third party source code that is not
properly acknowledged, please get in touch.

### Bug Fixes
We haven't had to fix many bugs that were in Akka HTTP 10.2.10.

* Use Java NIO Files#createTempFile [PR170](https://github.com/apache/pekko-http/pull/170) (Akka HTTP [CVE-2023-33251](https://akka.io/security/akka-http-cve-2023-05-15.html))

### Additions
* Scala 3 support ([PR150](https://github.com/apache/pekko-http/pull/150))
* Added pekko-http-cors ([PR208](https://github.com/apache/pekko-http/pull/208))

### Other Changes
* Remove http2-support module. This module was only kept for compatibility reasons before. Its code has long been part of the pekko-http-core ([PR52](https://github.com/apache/pekko-http/pull/52))

### Dependency Upgrades
We have tried to limit the changes to third party dependencies that are used in Pekko HTTP 1.0.0. These are some exceptions:

* parboiled 2.5.0 ([PR14](https://github.com/apache/pekko-http/pull/14)). We have dropped the use of the internal Akka/Pekko copy of parboiled in favour of using the jar directly. There have been reports of small binary compatibility issues between parboiled 2.4.1 and parboiled 2.5.0 ([#174](https://github.com/apache/pekko-http/issues/174)).
* jackson 2.14.3
* scalatest 3.2.14. Pekko users who have existing tests based on Akka Testkit may need to migrate their tests due to the scalatest upgrade. The [scalatest 3.2 release notes](https://www.scalatest.org/release_notes/3.2.0) have a detailed description of the changes needed.
