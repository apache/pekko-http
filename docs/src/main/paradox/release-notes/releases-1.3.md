# 0. Release Notes (1.3.x)

## 1.3.0

Release notes for Apache Pekko HTTP 1.3.0. See [GitHub Milestone for 1.3.0](https://github.com/apache/pekko-http/milestone/9?closed=1) for a fuller list of changes.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars (or later 1.x releases) where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.3 jars are built with Pekko 1.1 jars.

### Changes
* Avoid using deprecated methods from Pekko Core libs
* Deprecate some methods that will be removed in 2.0.0 ([PR758](https://github.com/apache/pekko-http/pull/758))
* Add mjs extension for javascript media type ([PR683](https://github.com/apache/pekko-http/pull/683))
* Avoid sun.misc.Unsafe in StringTools ([PR690](https://github.com/apache/pekko-http/pull/690))
* HttpChallenge: avoid pekko Java API Option class ([PR709](https://github.com/apache/pekko-http/pull/709))
* Avoid Scala classes in Java example ([PR741](https://github.com/apache/pekko-http/pull/741))
* Small improvements to ByteString usage ([PR799](https://github.com/apache/pekko-http/pull/799), [PR821](https://github.com/apache/pekko-http/pull/821), [PR827](https://github.com/apache/pekko-http/pull/827))
* Add `pekko.http.server.enable-http2` config and deprecate preview settings ([PR847](https://github.com/apache/pekko-http/pull/847))

### Additions
* Add Trailer header ([PR684](https://github.com/apache/pekko-http/pull/684))
* Add Java Duration version of withResponseEntitySubscriptionTimeout ([PR789](https://github.com/apache/pekko-http/pull/789))
* SSE Client Oversized Messages Handling ([PR846](https://github.com/apache/pekko-http/pull/846))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Jackson 2.20.0
* scala-xml 2.4.0
