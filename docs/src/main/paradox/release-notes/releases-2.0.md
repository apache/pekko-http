# 0. Release Notes (2.0.x)

## 2.0.0

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
