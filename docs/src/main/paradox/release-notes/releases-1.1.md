# 0. Release Notes (1.1.x)

## 1.1.0-M1

Release notes for Apache Pekko HTTP 1.1.0-M1. See [GitHub Milestone](https://github.com/apache/pekko-http/milestone/1?closed=1) for fuller list of changes.
As with all milestone releases, this release is not recommended for production use - it is designed to allow users to try out the changes in a test environment.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.1 jars are built with Pekko 1.1 jars.

All the changes in the @ref:[1.0.x releases](releases-1.0.md) up to and including v1.0.1.

### Changes
* Changed names of HTTP status codes 413 and 422 ([PR87](https://github.com/apache/pekko-http/pull/87))
* Parse entire HTTP chunk size ([PR528](https://github.com/apache/pekko-http/pull/528))
* Increased default value of `pekko.http.server.stream-cancellation-delay` and `pekko.http.client.stream-cancellation-delay` from 100 to 1000 millis ([PR590](https://github.com/apache/pekko-http/pull/590))

### Additions
* Add UnsupportedContentTypeException Java DSL ([PR376](https://github.com/apache/pekko-http/pull/376))
* new pekko-http-testkit-munit module ([PR536](https://github.com/apache/pekko-http/pull/536))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* slf4j was updated to v2
* Jackson 2.17.1
