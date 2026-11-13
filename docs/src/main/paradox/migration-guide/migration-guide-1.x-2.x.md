# Migration from Apache Pekko HTTP 1.x to 2.x

Apache Pekko HTTP 2.x requires Apache Pekko 2.x. See the
@extref:[Pekko 1.x to 2.x migration guide](pekko-docs:migration/migration-guide-1.x-2.x.html) for the changes in the core
Pekko libraries.

## General changes

* Apache Pekko 2.x is the new minimum Pekko version.
* Java 17 is the new minimum Java version.
* Scala 2.12 is no longer supported.
* Deprecated code has been removed, including a number of util classes that existed for compatibility across
Scala versions. Before upgrading, build against the latest Pekko HTTP 1.x release and fix any deprecation warnings.
* Java DSL methods that returned a Scala `Duration` now return a `java.time.Duration`.
([PR788](https://github.com/apache/pekko-http/pull/788))
* The new `pekko-http-jackson3` module supports Jackson 3. See the configuration change below.
* `ClientTransport`, `RequestResponseAssociation`, `SimpleRequestResponseAttribute`, the Java DSL `anyOf`/`allOf`
directive combinators and the `storeUploadedFile`, `storeUploadedFiles` and `fileUploadAll` directives are no longer
marked `@ApiMayChange` and are now stable APIs. ([PR1364](https://github.com/apache/pekko-http/pull/1364))
* `HttpMessage.attribute` and the `ResponseEntity.fromJava` implicit conversion no longer take an implicit
`JavaMapping` parameter, so they can be used from Scala 3.10. This is source compatible but changes their binary
signatures. ([PR1340](https://github.com/apache/pekko-http/pull/1340))
* The Java DSL `ConnectionPoolSettings.withHostOverrides` takes a `java.util.List<Pair<String, ConnectionPoolSettings>>`
(using `org.apache.pekko.japi.Pair`) instead of a list of `scala.Tuple2`. Java code that builds the overrides with
`Tuple2` has to be changed to use `Pair.create(pattern, settings)`. ([PR1377](https://github.com/apache/pekko-http/pull/1377))
* Many Java DSL methods accept wildcard generic types, such as `Iterable<? extends HttpHeader>` instead of
`Iterable<HttpHeader>`, so collections of subtypes can be passed. The erased signatures are unchanged.
([PR1379](https://github.com/apache/pekko-http/pull/1379), [PR1380](https://github.com/apache/pekko-http/pull/1380),
[PR1381](https://github.com/apache/pekko-http/pull/1381), [PR1382](https://github.com/apache/pekko-http/pull/1382),
[PR1384](https://github.com/apache/pekko-http/pull/1384))
* Java DSL alternatives to Scala types have been added: `java.time.Duration` accessors for `ServerSettings` timeouts
([PR1373](https://github.com/apache/pekko-http/pull/1373)), `onComplete` overloads that take a
`CompletionStage.handle` style callback instead of a `scala.util.Try` ([PR1375](https://github.com/apache/pekko-http/pull/1375))
and connection pool flows that emit an `HttpResponseResult` instead of a `scala.util.Try`
([PR1376](https://github.com/apache/pekko-http/pull/1376)).

## Behavior changes

Some of the changes listed below were made in Pekko HTTP 1.4.x releases. They are included here for users upgrading
from older 1.x versions, with a note of the release that introduced them.

### URL-encoded form data

The default `application/x-www-form-urlencoded` unmarshaller now ignores empty components between `&` separators,
including leading and trailing separators. For example, `&a=1&&` produces only the field `a=1`, rather than additional
empty-name fields. This matches the empty-component handling in the
[URL Standard form parser](https://url.spec.whatwg.org/#urlencoded-parsing).
Explicit empty-name fields (`=` and `=x`) and nonempty names without `=` remain supported, and charset handling is unchanged.

Applications relying on the additional empty-name fields should account for this behavior change. It also affects
round-tripping a manually constructed `FormData` containing `"" -> Uri.Query.EmptyValue`: this tuple renders as an
empty component and is discarded when unmarshalling. Use `"" -> ""` to represent an explicit empty-name, empty-value
field, which renders as `=` and is preserved. Generic `Uri.Query` parsing and rendering are unchanged.

### HTTP/1.1 parsing and rendering

* Whether a response renders `Content-Length` now depends on the request method as well as the status code, following
RFC 9110: a `2xx` response to `CONNECT` no longer renders it, and a `205 Reset Content` response now does.
`HttpMethod.custom` has a new overload with a `contentLengthAllowed` parameter; the old overload is deprecated.
Added in Pekko HTTP 1.4.0 ([PR968](https://github.com/apache/pekko-http/pull/968)).
A `HEAD` response renders `Content-Length` only when its entity declares a length greater than zero. Added in Pekko
HTTP 1.4.1 ([PR1244](https://github.com/apache/pekko-http/pull/1244))
* A `Content-Length` value with whitespace between its digits, or without any digit, is rejected; it was previously
read as the concatenated digits, or as `0`. Added in Pekko HTTP 1.4.1 ([PR1243](https://github.com/apache/pekko-http/pull/1243))
* Repeated `Connection` headers count towards `pekko.http.parsing.max-header-count`. Added in Pekko HTTP 1.4.1
([PR1268](https://github.com/apache/pekko-http/pull/1268))
* A message whose `Transfer-Encoding` value cannot be parsed is rejected. Added in Pekko HTTP 1.4.2
([PR1267](https://github.com/apache/pekko-http/pull/1267))
* Whitespace between or before chunk-size digits is rejected; trailing whitespace is still tolerated. Added in Pekko
HTTP 1.4.2 ([PR1257](https://github.com/apache/pekko-http/pull/1257))
* A request line where a CR or LF ends the request target is rejected with `505 HTTP Version Not Supported`.
([PR1341](https://github.com/apache/pekko-http/pull/1341))
* Headers whose rendered name or value contains CR, LF or NUL are dropped instead of being sent. This covers trailer
headers and chunk extensions as well as the main header block.
Added in Pekko HTTP 1.4.1 for CR and LF in trailers and chunk extensions ([PR1270](https://github.com/apache/pekko-http/pull/1270))
and NUL in headers ([PR1272](https://github.com/apache/pekko-http/pull/1272)); chunk extensions containing NUL are
dropped from Pekko HTTP 2.0.0 ([PR1344](https://github.com/apache/pekko-http/pull/1344)).
* `Link` header parameter values that contain a `:`, such as URI relation types, are now rendered quoted
(`rel="http://example.net/foo"`), as RFC 8288 requires. The unquoted form is still accepted when parsing.
([PR1365](https://github.com/apache/pekko-http/pull/1365))
* Comma-separated list headers, such as `Cache-Control`, `Connection`, `Accept` or `If-None-Match`, accept and ignore
empty list elements (`no-store,, public`, `, no-store` or `no-store,`) as RFC 9110 section 5.6.1 requires, instead of
staying a `RawHeader`. A list that must have at least one element and contains only empty elements, such as
`Cache-Control: ,`, is still not parsed. ([#1396](https://github.com/apache/pekko-http/issues/1396))
Whitespace before a list separator (`no-store , public`) is also accepted.
([PR1395](https://github.com/apache/pekko-http/pull/1395))
* Parse error details logged with `pekko.http.parsing.error-logging-verbosity = full` (the default), and the
`toString` of `IllegalRequestContext` passed to a custom `ParsingErrorHandler`, now escape control characters in the
raw request target or header value. ([PR1294](https://github.com/apache/pekko-http/pull/1294))

### HTTP/2

* Outgoing header fields whose name or value contains CR, LF or NUL are dropped. Added in Pekko HTTP 1.4.1
([PR1271](https://github.com/apache/pekko-http/pull/1271))
* An incoming request with a header field containing CR, LF or NUL is rejected. In Pekko HTTP 1.4.2 the connection is
closed with `GOAWAY(PROTOCOL_ERROR)`; from Pekko HTTP 2.0.0 the stream is answered with `400 Bad Request` and the
connection is kept. ([PR1297](https://github.com/apache/pekko-http/pull/1297))
* A request with an invalid `:scheme` pseudo-header, or with a regular field name containing `:`, is rejected with a
`400 Bad Request` for that stream; an invalid `:scheme` no longer fails the whole connection.
([PR1341](https://github.com/apache/pekko-http/pull/1341))
* A field name containing an uppercase letter or another character that RFC 9113 excludes, or a field value with
leading or trailing whitespace, makes the request (`400 Bad Request` for that stream) or response (protocol error)
malformed instead of being silently normalized. Pekko HTTP's own client and server always send lowercase field names.
([PR1343](https://github.com/apache/pekko-http/pull/1343))

* The HTTP/2 client sends the connection's scheme and authority in the `:scheme` and `:authority` pseudo-headers for
a request with a relative URI, instead of leaving them empty. A non-empty `Host` header supplies the authority first.
A Pekko HTTP server now sees the full request URI for such requests.
([PR1342](https://github.com/apache/pekko-http/pull/1342))
* When an HTTP/2 managed persistent connection gives up after its last connection attempt fails, the request that
triggered the attempts is answered with a `503 Service Unavailable` response, which carries the request's attributes,
before the stream fails. ([PR1368](https://github.com/apache/pekko-http/pull/1368))

### Server

* During graceful termination, a response for a request that is already being handled is delivered (with
`Connection: close`) instead of being lost when the request side completes or the network side pulls late.
([PR1398](https://github.com/apache/pekko-http/pull/1398), [PR1400](https://github.com/apache/pekko-http/pull/1400))

### Client

* After an early response (a response that completes before the streamed request entity has been fully sent), the
client connection pool no longer reuses the connection. If the response is an error (4xx or 5xx) or carries
`Connection: close`, the pool stops sending the request entity and closes the connection immediately; otherwise it
finishes sending the request entity and then closes the connection. ([PR1350](https://github.com/apache/pekko-http/pull/1350))

### Routing DSL

* The CORS directives accept an actual request only when every origin in its `Origin` header is allowed, instead of
when any one of them is. Added in Pekko HTTP 1.4.2 ([PR1262](https://github.com/apache/pekko-http/pull/1262))
* `FileAndResourceDirectives` compare path elements, rather than string prefixes, when checking that a served file
is below the base directory, so a symbolic link to a sibling directory such as `/var/www-private` is no longer served
from `/var/www`. Added in Pekko HTTP 1.4.1 ([PR1269](https://github.com/apache/pekko-http/pull/1269))
* `getFromResource` rejects a resource name that contains a `..` segment, a backslash or a NUL character, instead of
passing it to the class loader. Such names only ever resolved against directory-backed class loaders, not against
resources inside jar files. Set `pekko.http.routing.reject-unsafe-resource-names = off` to restore the previous
behavior. ([PR1406](https://github.com/apache/pekko-http/pull/1406))
* Directory listings rendered by `listDirectoryContents` and `getFromBrowseableDirectory` escape HTML in file names.
Added in Pekko HTTP 1.4.2 ([PR1191](https://github.com/apache/pekko-http/pull/1191))
* Server-Sent Events fields whose values contain Unicode line separators, such as U+2028, are parsed instead of being
ignored. Added in Pekko HTTP 1.4.1 ([PR1186](https://github.com/apache/pekko-http/pull/1186))

## Configuration Changes in Pekko HTTP 2.x

The `reference.conf` defaults have changed in a number of places. If you override any of the settings
below in your `application.conf`, or rely on their Pekko HTTP 1.x defaults, review this list.

Some of the settings listed below were added in recent Pekko HTTP 1.x releases. They are included here for users
upgrading from older 1.x versions, with a note of the release that introduced them.

### Changed default values

* `pekko.http.server.enable-http2` changed from `off` to `on`. The setting was added in Pekko HTTP 1.3.0
to replace `pekko.http.server.preview.enable-http2` ([PR818](https://github.com/apache/pekko-http/pull/818)).
Servers bound with `Http().newServerAt(...).bind(...)` now accept HTTP/2 connections (via ALPN for HTTPS, or
prior knowledge and the `h2c` upgrade for plain HTTP). `bindFlow` and `connectionSource()` do not support HTTP/2.
Set it to `off`, and leave `pekko.http.server.preview.enable-http2` unset or `off`, to restore the Pekko HTTP 1.x
behavior. See @ref:[Server-Side HTTP/2](../server-side/http2.md). ([PR928](https://github.com/apache/pekko-http/pull/928))
* `pekko.http.server.preview.enable-http2` changed to `null` (it was `off` up to Pekko HTTP 1.2.x and
`${pekko.http.server.enable-http2}` in 1.3.x and 1.4.x). The setting is still supported for compatibility:
HTTP/2 is enabled if either setting is `on`. ([PR928](https://github.com/apache/pekko-http/pull/928))
* `pekko.http.server.http2.frame-type-throttle.frame-types` changed from `[]` to `["reset", "empty-data-no-end-stream"]`,
so the rate of incoming `RST_STREAM` frames on a server connection is limited by default to mitigate HTTP/2 Rapid
Reset attacks (CVE-2023-44487), as is the rate of incoming DATA frames that carry no payload and do not end their
stream, which consume no flow-control window (Empty Frames Flood, CVE-2019-9518). All throttled frames share one
budget: with the default `cost`, `burst` and `interval` settings, a client that sends more than about 100 of them
per second has its connection closed with `GOAWAY(ENHANCE_YOUR_CALM)`
([PR1413](https://github.com/apache/pekko-http/pull/1413)). Clients that legitimately cancel many streams, such as some gRPC clients, may
need a higher limit. The empty DATA frame with END_STREAM that closes a request body is not charged unless
`empty-data` is added. Set it to `[]` to disable throttling. ([PR1193](https://github.com/apache/pekko-http/pull/1193),
[PR1265](https://github.com/apache/pekko-http/pull/1265))

### Removed configuration

* `pekko.http.server.remote-address-header` was removed (deprecated since Akka HTTP 10.2.0); use
`pekko.http.server.remote-address-attribute` instead. ([PR757](https://github.com/apache/pekko-http/pull/757))

### New configuration

The full `reference.conf` for each module, with descriptions of every setting, is listed in the
@ref:[configuration reference](../configuration.md).

pekko-http-core server settings:

* `pekko.http.server.http2.max-header-list-size` (default `64 KiB`) bounds the decompressed size of an incoming
HTTP/2 header block, and the accumulated HEADERS and CONTINUATION fragments of a header block; a larger header block
is rejected with a `GOAWAY(ENHANCE_YOUR_CALM)` frame. The same setting was added for the client as
`pekko.http.client.http2.max-header-list-size`. These settings were added in Pekko HTTP 1.4.1.
([PR1216](https://github.com/apache/pekko-http/pull/1216))
* `pekko.http.server.http2.max-frame-size` (default `512kB`) bounds the largest incoming HTTP/2 frame payload;
a larger frame is rejected with a `FRAME_SIZE_ERROR`. The same setting was added for the client as
`pekko.http.client.http2.max-frame-size`. ([PR1264](https://github.com/apache/pekko-http/pull/1264))
* `pekko.http.server.http2.max-connection-age`, `pekko.http.server.http2.max-connection-age-grace` and
`pekko.http.server.http2.max-connection-age-jitter` limit the lifetime of HTTP/2 server connections, which helps
to rebalance long-lived connections across server instances. The feature is disabled by default.
See @ref:[Limiting the lifetime of connections](../server-side/http2.md#limiting-the-lifetime-of-connections).
([PR1316](https://github.com/apache/pekko-http/pull/1316), [PR1323](https://github.com/apache/pekko-http/pull/1323))
* `pekko.http.server.websocket.compression` is a new section configuring server-side WebSocket compression
(the RFC 7692 `permessage-deflate` extension). It is `enabled` by default and used only when the client requests it
during the handshake. Most browsers request it, so WebSocket connections from browsers are compressed by default
after upgrading; set `pekko.http.server.websocket.compression.enabled = false` to turn it off. `max-allocation` (default `256k`) bounds the size of a decompressed message.
See @ref:[WebSocket compression](../server-side/websocket-support.md#websocket-compression).
([PR1114](https://github.com/apache/pekko-http/pull/1114), [PR1173](https://github.com/apache/pekko-http/pull/1173))
* `pekko.http.server.websocket.receive-idle-timeout` and `pekko.http.server.websocket.send-idle-timeout` (default
`infinite`, which disables them) fail a WebSocket connection with a `WebSocketIdleTimeoutException` when no frame is
received or sent within the timeout. Ping and Pong frames count as activity. The same settings were added for the
client under `pekko.http.client.websocket`. ([PR1369](https://github.com/apache/pekko-http/pull/1369))

pekko-http-core client settings:

* `pekko.http.client.http2.persistent-connection-max-age` and
`pekko.http.client.http2.persistent-connection-max-age-jitter` limit the lifetime of connections created by
`managedPersistentHttp2` and `managedPersistentHttp2WithPriorKnowledge`. The feature is disabled by default.
See @ref:[Limiting managed persistent connection lifetime](../client-side/http2.md#limiting-managed-persistent-connection-lifetime).
([PR1320](https://github.com/apache/pekko-http/pull/1320))
* `pekko.http.client.http2.frame-type-throttle` limits the rate of incoming frames of the configured types on an
HTTP/2 client connection, like `pekko.http.server.http2.frame-type-throttle` does for the server and with the same
settings. By default, `frame-types = ["reset", "empty-data-no-end-stream"]`: a server that sends more than about 100
`RST_STREAM` frames, or DATA frames that carry no payload and do not end their stream, per second has the connection
closed with `GOAWAY(ENHANCE_YOUR_CALM)`. A client with many requests in flight on one connection may need a higher limit. Set `frame-types` to `[]` to
disable throttling. ([#1393](https://github.com/apache/pekko-http/issues/1393))

pekko-http-core parsing settings:

* `pekko.http.parsing.max-chunk-count` (default `100000`) bounds the number of chunks in a chunked entity;
a larger entity fails the entity stream.
([PR1195](https://github.com/apache/pekko-http/pull/1195))
* `pekko.http.parsing.max-part-count` (default `10000`) bounds the number of body parts in a multipart entity.
Applications that receive large multipart uploads may need to raise these limits.
([PR1266](https://github.com/apache/pekko-http/pull/1266))

pekko-http settings:

* `pekko.http.sse.oversized-line-handling` and `pekko.http.sse.oversized-event-handling` (default `"fail-stream"`)
control how Server-Sent Events lines and events that exceed `max-line-size` and `max-event-size` are handled.
The other options are `"log-and-skip"`, `"truncate"` and `"dead-letter"`. These settings were added in
Pekko HTTP 1.3.0. See @ref:[Oversized Message Handling](../common/sse-support.md#oversized-message-handling).
([PR744](https://github.com/apache/pekko-http/pull/744))
* `pekko.http.routing.use-jar-file-cache` (default `on`) controls whether `FileAndResourceDirectives` use the JDK's
jar file cache when serving resources from jar files. Turn it off if those jar files have to be replaceable while
the server is running. ([PR1217](https://github.com/apache/pekko-http/pull/1217))
* `pekko.http.routing.reject-unsafe-resource-names` (default `on`) controls whether `getFromResource` rejects resource
names that contain a `..` segment, a backslash or a NUL character. See @ref:[Routing DSL](#routing-dsl).

pekko-http-testkit settings:

* `pekko.http.testkit.routes.timeout` (default `1 s`) sets the default `RouteTestTimeout` used by the
Scala testkit. See @ref:[Increase Timeout](../routing-dsl/testkit.md#increase-timeout).
([PR1016](https://github.com/apache/pekko-http/pull/1016))

pekko-http-jackson3 settings:

* The new `pekko-http-jackson3` module (based on Jackson 3) is configured under `pekko.http.marshallers.jackson3`,
mirroring the `pekko.http.marshallers.jackson` settings. ([PR837](https://github.com/apache/pekko-http/pull/837))
