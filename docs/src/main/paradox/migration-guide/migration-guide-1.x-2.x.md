# Migration from Apache Pekko HTTP 1.x to 2.x

Apache Pekko HTTP 2.x requires Apache Pekko 2.x. See the
@extref:[Pekko 1.x to 2.x migration guide](pekko-docs:migration/migration-guide-1.x-2.x.html) for the changes in the core
Pekko libraries.

## Configuration Changes in Pekko HTTP 2.x

The `reference.conf` defaults have changed in a number of places. If you override any of the settings
below in your `application.conf`, or rely on their Pekko HTTP 1.x defaults, review this list.

Some of the settings listed below were added in recent Pekko HTTP 1.x releases. They are included here for users
upgrading from older 1.x versions, with a note of the release that introduced them.

### Changed default values

* `pekko.http.server.enable-http2` changed from `off` to `on`. The setting was added in Pekko HTTP 1.3.0
to replace `pekko.http.server.preview.enable-http2` ([PR818](https://github.com/apache/pekko-http/pull/818)). Servers bound with `Http().newServerAt(...).bind(...)`
now accept HTTP/2 connections (via ALPN for HTTPS, or prior knowledge and the `h2c` upgrade for plain HTTP). `bindFlow` and
`connectionSource()` do not support HTTP/2. Set it to `off` to restore the Pekko HTTP 1.x behavior.
See @ref:[Server-Side HTTP/2](../server-side/http2.md). ([PR928](https://github.com/apache/pekko-http/pull/928))
* `pekko.http.server.preview.enable-http2` changed to `null` (it was `off` up to Pekko HTTP 1.2.x and
`${pekko.http.server.enable-http2}` in 1.3.x and 1.4.x). The setting
is still supported for compatibility, but it is now ignored unless set explicitly, and it is ignored if
`pekko.http.server.enable-http2` is `on`. ([PR928](https://github.com/apache/pekko-http/pull/928))
* `pekko.http.server.http2.frame-type-throttle.frame-types` changed from `[]` to `["reset"]`, so incoming
`RST_STREAM` frames are throttled by default to mitigate HTTP/2 Rapid Reset attacks (CVE-2023-44487).
Set it to `[]` to disable throttling. ([PR1193](https://github.com/apache/pekko-http/pull/1193))

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
during the handshake; `max-allocation` (default `256k`) bounds the size of a decompressed message.
See @ref:[WebSocket compression](../server-side/websocket-support.md#websocket-compression).
([PR1114](https://github.com/apache/pekko-http/pull/1114), [PR1173](https://github.com/apache/pekko-http/pull/1173))

pekko-http-core client settings:

* `pekko.http.client.http2.persistent-connection-max-age` and
`pekko.http.client.http2.persistent-connection-max-age-jitter` limit the lifetime of connections created by
`managedPersistentHttp2` and `managedPersistentHttp2WithPriorKnowledge`. The feature is disabled by default.
See @ref:[Limiting managed persistent connection lifetime](../client-side/http2.md#limiting-managed-persistent-connection-lifetime).
([PR1320](https://github.com/apache/pekko-http/pull/1320))

pekko-http-core parsing settings:

* `pekko.http.parsing.max-chunk-count` (default `100000`) bounds the number of chunks in a chunked entity.
([PR1195](https://github.com/apache/pekko-http/pull/1195))
* `pekko.http.parsing.max-part-count` (default `10000`) bounds the number of body parts in a multipart entity.
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

pekko-http-testkit settings:

* `pekko.http.testkit.routes.timeout` (default `1 s`) sets the default `RouteTestTimeout`.
See @ref:[Accounting for Slow Test Systems](../routing-dsl/testkit.md#accounting-for-slow-test-systems).
([PR1016](https://github.com/apache/pekko-http/pull/1016))

pekko-http-jackson3 settings:

* The new `pekko-http-jackson3` module (based on Jackson 3) is configured under `pekko.http.marshallers.jackson3`,
mirroring the `pekko.http.marshallers.jackson` settings. ([PR837](https://github.com/apache/pekko-http/pull/837))
