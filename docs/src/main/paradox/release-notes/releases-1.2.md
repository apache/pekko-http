# 0. Release Notes (1.2.x)

## 1.2.0

Release notes for Apache Pekko HTTP 1.2.0. See [GitHub Milestone for 1.2.0](https://github.com/apache/pekko-http/milestone/7?closed=1) and [GitHub Milestone for 1.1.1](https://github.com/apache/pekko-http/milestone/3?closed=1) for a fuller list of changes.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars (or later 1.x releases) where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.2 jars are built with Pekko 1.1 jars.

### Bug Fix
* fix partial match in HTTP2 HttpMessageRendering class ([PR622](https://github.com/apache/pekko-http/pull/622))

### Changes
* use convertIterable to wrap java Iterables ([PR613](https://github.com/apache/pekko-http/pull/613))
* add `@noinline` annotation to help fix some issues with Kamon metrics ([PR630](https://github.com/apache/pekko-http/pull/630))
* Optimized Uri.withQuery ([PR637](https://github.com/apache/pekko-http/pull/637))
* Make AddFutureAwaitResult an AnyVal ([PR647](https://github.com/apache/pekko-http/pull/647))

### Additions
* Get string representation of javadsl Content and Media Type ([PR616](https://github.com/apache/pekko-http/pull/616))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Jackson 2.18.4
* scala-xml 2.3.0
* scala 3.3.6
