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

This setting only applies to the HTTP/1.1 client, which includes the connection pool backing
@ref[request-level](request-level.md) and @ref[host-level](host-level.md) APIs.

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
