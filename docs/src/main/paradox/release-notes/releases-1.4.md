# 0. Release Notes (1.4.x)

## 1.4.1

Release notes for Apache Pekko HTTP 1.4.1. See [GitHub Milestone for 1.4.1](https://github.com/apache/pekko-http/milestone/16?closed=1) for a fuller list of changes.

This is a bug fix release. It includes a number of hardening changes to the HTTP parsing and rendering code.

### Bug Fixes
* SSE: allow Unicode line terminators ([PR1186](https://github.com/apache/pekko-http/pull/1186))
* Use `Locale.ROOT` for `toLowerCase`/`toUpperCase` and in the `RouteTest` header lookup ([PR1224](https://github.com/apache/pekko-http/pull/1224))
* Parse `Content-Length` as digits with surrounding whitespace only ([PR1243](https://github.com/apache/pekko-http/pull/1243))
* Render `Content-Length` for HEAD responses with a declared length ([PR1244](https://github.com/apache/pekko-http/pull/1244))
* HTTP/2: bound incoming header blocks with `max-header-list-size` ([PR1247](https://github.com/apache/pekko-http/pull/1247))
* Count repeated `Connection` headers towards `max-header-count` ([PR1268](https://github.com/apache/pekko-http/pull/1268))
* Compare path elements when checking that a served file is below the base directory ([PR1269](https://github.com/apache/pekko-http/pull/1269))
* Prevent CRLF injection through chunk trailers and extensions ([PR1270](https://github.com/apache/pekko-http/pull/1270))
* HTTP/2: drop header fields containing CR, LF or NUL ([PR1271](https://github.com/apache/pekko-http/pull/1271))
* Drop rendered headers containing NUL as well as CR and LF ([PR1272](https://github.com/apache/pekko-http/pull/1272))

### Dependency Changes

There are no dependency changes in this release.

## 1.4.0

Release notes for Apache Pekko HTTP 1.4.0. See [GitHub Milestone for 1.4.0](https://github.com/apache/pekko-http/milestone/13?closed=1) for a fuller list of changes.

It is strongly recommended that you avoid using Pekko 1.0.x jars with this release, you should use Pekko 1.1.x jars (or later 1.x releases) where possible. We don't expect there to be problems running with Pekko 1.0.x jars but Pekko HTTP 1.4 jars are built with Pekko 1.1 jars.

### Changes
* Don't rely on Pekko Core ccompat ([PR890](https://github.com/apache/pekko-http/pull/890))
* Correct Content-Length rendering based on method+status ([PR968](https://github.com/apache/pekko-http/pull/968))
* Ensure end() is called on Inflaters and Deflaters to allow earlier tidy up of resources ([#1133](https://github.com/apache/pekko-http/issue/1133))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Jackson 2.21.5
