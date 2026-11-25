# 0. Release Notes (2.0.x)

@@@ warning

Pekko HTTP 2.x and Pekko 2.x are not binary compatible with their 1.x releases. Libraries built against
Pekko 1.x or Pekko HTTP 1.x cannot be trusted to work with Pekko HTTP 2.x, and need to be rebuilt and released for
Pekko 2.x. See the @extref:[Pekko 2.0 release notes](pekko-docs:release-notes/releases-2.0.html) for the binary
compatibility changes in the core Pekko modules.

Read the @ref:[migration guide](../migration-guide/migration-guide-1.x-2.x.md) before upgrading from Pekko HTTP 1.x,
and upgrade directly to the latest 2.0.x release.

@@@

## 2.0.1

Release notes for Apache Pekko HTTP 2.0.1. See [GitHub Milestone for 2.0.1](https://github.com/apache/pekko-http/milestone/21?closed=1) for a fuller list of changes.

This is a bug fix release. It is binary compatible with Pekko HTTP 2.0.0. A few of the fixes change behavior in
edge cases; see the notes below.

### Bug Fixes
* Validate custom status code reason phrases. Constructing a `StatusCode` whose reason contains a control
  character other than HTAB, DEL, or a character above `U+00FF` now throws `IllegalArgumentException`. When a
  response with an unregistered status code is parsed, such characters are dropped from the received reason
  ([PR1452](https://github.com/apache/pekko-http/pull/1452))
* HTTP/2: decode UTF-8 in header values as HTTP/1.1 does. A value such as `café` was previously surfaced as
  `cafÃ©` ([PR1500](https://github.com/apache/pekko-http/pull/1500))
* Keep the line end after an invalid UTF-8 sequence in a header value, and keep obs-text octets (above `0x7F`)
  in header values instead of rejecting them ([PR1499](https://github.com/apache/pekko-http/pull/1499), [PR1518](https://github.com/apache/pekko-http/pull/1518))
* HTTP/2: drop connection-specific fields (`Connection`, `Keep-Alive`, `Proxy-Connection`, `Transfer-Encoding`
  and `Upgrade`) consistently from outbound headers and trailers, including when set as raw or custom headers.
  They are dropped with a warning ([PR1463](https://github.com/apache/pekko-http/pull/1463))
* Client: handle the framing of empty HTTP/1 `205 Reset Content` responses, so that an empty chunked `205` no
  longer corrupts the next response on a persistent connection. A `205` with a non-empty chunk or a non-zero
  `Content-Length` is rejected ([PR1464](https://github.com/apache/pekko-http/pull/1464))
* WebSocket: terminate empty compressed fragments correctly, and inflate an empty final fragment sent after a
  sync flush ([PR1427](https://github.com/apache/pekko-http/pull/1427))
* WebSocket: accept the registered close codes 1012 to 1014 ([PR1447](https://github.com/apache/pekko-http/pull/1447))

### Dependency Changes

* zstd-jni 1.5.7-28 (optional dependency, only needed for `Coders.Zstd`)

## 2.0.0

@@@ note

Read the binary compatibility warning at the top of this page before upgrading from Pekko HTTP 1.x.

@@@

Release notes for Apache Pekko HTTP 2.0.0. This is the first release of a new major version and includes the changes
from the 2.0.0-M1 and 2.0.0-M2 milestone releases. See the GitHub milestones for
[2.0.0-M1](https://github.com/apache/pekko-http/milestone/11?closed=1),
[2.0.0-M2](https://github.com/apache/pekko-http/milestone/14?closed=1) and
[2.0.0-M3](https://github.com/apache/pekko-http/milestone/17?closed=1) for a fuller list of changes.

Pekko HTTP 2.0.0 is not binary compatible with Pekko HTTP 1.x. Read the
@ref:[migration guide](../migration-guide/migration-guide-1.x-2.x.md) before upgrading. It lists the API, behavior
and configuration changes, including the changed default values.

### Main changes

* Pekko 2.0.0 is the new minimum Pekko version
* Java 17 is the new minimum Java version
* Scala 2.12 support has been dropped
* Deprecated code has been removed, including a number of util classes for Scala version compatibility
* HTTP/2 is enabled by default for servers bound with `Http().newServerAt(...).bind(...)` ([PR928](https://github.com/apache/pekko-http/pull/928))
* The rate of incoming `RST_STREAM` frames, and of empty DATA frames that do not end their stream, is limited by default on HTTP/2 server and client connections ([PR1193](https://github.com/apache/pekko-http/pull/1193), [PR1265](https://github.com/apache/pekko-http/pull/1265), [PR1394](https://github.com/apache/pekko-http/pull/1394)). A connection that exceeds the limit is closed with `GOAWAY(ENHANCE_YOUR_CALM)` ([PR1413](https://github.com/apache/pekko-http/pull/1413))
* `getFromResource` rejects resource names that contain a `..` segment, a backslash or a NUL character by default; set `pekko.http.routing.reject-unsafe-resource-names = off` to restore the 1.x behavior ([PR1406](https://github.com/apache/pekko-http/pull/1406))
* Jackson 3 is supported in the new `pekko-http-jackson3` module ([PR837](https://github.com/apache/pekko-http/pull/837))
* Java DSL methods that returned Scala Durations now return Java Durations ([PR788](https://github.com/apache/pekko-http/pull/788))

### Additions
* `zstd` content coding (`Coders.Zstd`), with a decoder window limited to 8 MB as RFC 9659 requires. It is not part of `Coders.DefaultCoders` and needs the optional `com.github.luben:zstd-jni` dependency (1.5.7-25 or later) ([PR1391](https://github.com/apache/pekko-http/pull/1391))
* `Coders.decodeMessage(message, decoders)` and Java DSL `Coder.decodeResponse` / `Coder.decodeRequest` decode a message with the decoder matching its `Content-Encoding` ([PR1391](https://github.com/apache/pekko-http/pull/1391))
* Server-side WebSocket compression ([PR1114](https://github.com/apache/pekko-http/pull/1114))
* WebSocket `receive-idle-timeout` and `send-idle-timeout` settings ([PR1369](https://github.com/apache/pekko-http/pull/1369))
* `max-connection-age` settings for HTTP/2 server connections ([PR1316](https://github.com/apache/pekko-http/pull/1316))
* Client-side maximum connection age for HTTP/2 managed persistent connections ([PR1320](https://github.com/apache/pekko-http/pull/1320))
* `HttpMessage.header(name)` for case-insensitive header lookup by name ([PR1366](https://github.com/apache/pekko-http/pull/1366))

### Java DSL improvements
* Use `java.time.Duration` in Java DSL `ServerSettings` timeouts ([PR1373](https://github.com/apache/pekko-http/pull/1373))
* Add `onComplete` overloads with a `CompletionStage.handle` style callback ([PR1375](https://github.com/apache/pekko-http/pull/1375))
* Add connection pool flows that emit `HttpResponseResult` instead of `scala.util.Try` ([PR1376](https://github.com/apache/pekko-http/pull/1376))
* `ConnectionPoolSettings.withHostOverrides` takes `japi.Pair` instead of `scala.Tuple2` ([PR1377](https://github.com/apache/pekko-http/pull/1377))
* Add `java.lang.Iterable` overloads to `Trailer.addHeaders` and `withHeaders` ([PR1374](https://github.com/apache/pekko-http/pull/1374))
* Accept wildcard generic types in Java API parameters ([PR1379](https://github.com/apache/pekko-http/pull/1379), [PR1380](https://github.com/apache/pekko-http/pull/1380), [PR1381](https://github.com/apache/pekko-http/pull/1381), [PR1382](https://github.com/apache/pekko-http/pull/1382), [PR1384](https://github.com/apache/pekko-http/pull/1384))
* Remove `@ApiMayChange` from client transport, request/response association and some directive APIs ([PR1364](https://github.com/apache/pekko-http/pull/1364))
* `HttpMessage.attribute` and `ResponseEntity.fromJava` no longer require a private `JavaMapping` implicit ([PR1340](https://github.com/apache/pekko-http/pull/1340))

### Bug Fixes
* Reject control bytes in the request line, and malformed HTTP/2 `:scheme` pseudo-headers and field names ([PR1341](https://github.com/apache/pekko-http/pull/1341))
* HTTP/2: send `:scheme` and `:authority` for a request with a relative URI ([PR1342](https://github.com/apache/pekko-http/pull/1342))
* HTTP/2: reject uppercase field names and field values with surrounding whitespace ([PR1343](https://github.com/apache/pekko-http/pull/1343))
* Drop chunk extensions containing NUL, not only CR or LF ([PR1344](https://github.com/apache/pekko-http/pull/1344))
* Escape control characters in logged parse-error details ([PR1294](https://github.com/apache/pekko-http/pull/1294))
* Close a pool connection after an early response, and abort the upload only if the request was rejected ([PR1350](https://github.com/apache/pekko-http/pull/1350))
* Quote `Link` header parameter values containing a colon ([PR1365](https://github.com/apache/pekko-http/pull/1365))
* HTTP/2: emit a failed response for the triggering request when a persistent connection gives up ([PR1368](https://github.com/apache/pekko-http/pull/1368))
* Accept whitespace before the separator, and empty elements, in HTTP header lists ([PR1395](https://github.com/apache/pekko-http/pull/1395), [PR1407](https://github.com/apache/pekko-http/pull/1407))
* Preserve pending responses during server shutdown ([PR1398](https://github.com/apache/pekko-http/pull/1398), [PR1400](https://github.com/apache/pekko-http/pull/1400))

This release also includes the bug fixes from the Pekko HTTP 1.4.x releases. See the
@ref:[1.4 release notes](releases-1.4.md).

### Performance
* Huffman decode HPACK string literals into reusable scratch arrays ([PR1302](https://github.com/apache/pekko-http/pull/1302))
* Copy ASCII bytes with the bulk `String.getBytes` ([PR1299](https://github.com/apache/pekko-http/pull/1299))

### Dependency Changes

Most of the dependency changes are small patch level upgrades. Some exceptions include:

* Pekko 2.0.0
* Jackson 2.23.0
* Jackson 3.3.0 (new `pekko-http-jackson3` module)
* scala-xml 2.5.0
