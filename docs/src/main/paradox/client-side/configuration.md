# Configuration

HTTP client settings are split into different sections  
 
 * `pekko.http.client`: basic client settings
 * `pekko.http.host-connection-pool`: pool settings
 
## Basic Client Settings

These settings influence the basic library behavior for each HTTP connection. When changed in the application's
`application.conf` they globally affect the default client behavior.

Basic client settings can be overridden in multiple ways:

 * by passing custom @apidoc[ClientConnectionSettings] instances to APIs in @apidoc[Http$]
 * by overriding settings in `pekko.http.host-connection-pool.client`, these overrides will take effect whenever a pool is used
   like with `Http().singleRequest`
 * by putting custom @apidoc[ClientConnectionSettings] into @apidoc[ConnectionPoolSettings] and passing those to APIs in `Http`
 * by using [per-host overrides](#per-host-overrides)

@@snip [reference.conf](/http-core/src/main/resources/reference.conf) { #client-settings }

## Strict Response Entities

By default response entities are streamed, so the application has to consume (or discard) each response entity before
the connection can be used for the next request. Setting `pekko.http.client.strict-response-entity-timeout` to a
duration makes the client collect every response entity into a strict entity (`HttpEntity.Strict`) before the response is
dispatched to the application:

```
pekko.http.client.strict-response-entity-timeout = 10s
```

Keep in mind that this buffers each complete response body in memory. A response that is not fully received within the
configured duration fails with a `TimeoutException`, and one that exceeds
`pekko.http.client.strict-response-entity-max-bytes` (8 MB by default) fails with an `EntityStreamException`; in both
cases the connection is failed. Trailing headers of chunked responses are dropped, as they are with
`HttpEntity.toStrict`.

This applies to the HTTP/1.1 client, which includes the connection pool backing the
@ref[request-level](request-level.md) and @ref[host-level](host-level.md) APIs, and to the
@ref[HTTP/2 client](http2.md).

### On HTTP/2

Responses on an HTTP/1.1 connection are sequential, so collecting one response entity never delays another. On HTTP/2
several requests are in flight on one connection at the same time, which brings a few things to be aware of:

 * Entities are collected for up to `pekko.http.client.http2.max-concurrent-streams` responses concurrently, so that a
   large response does not hold up smaller ones on other streams.
 * Responses are emitted in the order in which their entities complete, not in the order in which their headers
   arrived. HTTP/2 responses are unordered anyway and have to be correlated to their request via a
   @apidoc[RequestResponseAssociation], so this does not break the API contract, but it does change observed ordering.
 * Worst-case memory usage per connection is `max-concurrent-streams` × `strict-response-entity-max-bytes`, which is
   256 × 8 MB with the defaults. Tune both settings for the responses you actually expect.
 * A response entity that times out or exceeds the maximum fails the whole connection, and with it every other stream
   in flight on that connection. The remaining body cannot be skipped safely, so there is no way to fail only the one
   response. With `Http().connectionTo(host).managedPersistentHttp2()` the connection is re-established afterwards
   according to `pekko.http.client.http2.max-persistent-attempts`.
 * Entity data is read from the network as fast as the peer sends it (up to the configured maximum) instead of at the
   pace the application consumes it, so HTTP/2 flow control no longer reflects application backpressure for response
   bodies.

## Pool Settings

Pool settings influence the behavior of client connection pools as used with APIs like `Http.singleRequest`
(see @ref[request-level](request-level.md) and @ref[host-level](host-level.md)).

This includes the amount of total concurrent connections a pool should open to a target host and other settings.
These settings include a (by default empty) section `client` that can be used to override basic client settings
when used in the context of a pool.

Pool settings can be overridden on a [per-target-host](#per-host-overrides) basis.

@@snip [reference.conf](/http-core/src/main/resources/reference.conf) { #pool-settings }

## Per Host Overrides

Settings can be overridden on a per-host basis by creating a list of `host-patterns` together with overridden settings
in the `pekko.http.host-connection-pool.per-host-override` setting.

Note that only the first matching entry is selected and used even if multiple entries would match.

@@snip [reference.conf](/http-core/src/main/resources/reference.conf) { #per-host-overrides }

## Precedence of settings

When using pool APIs, settings take precedence like this (highest precedence first):

 * client settings in first `per-host-override` entry whose `host-pattern` matches the given target host
 * settings in `pekko.http.host-connection-pool.client`
 * settings in `pekko.http.client`
