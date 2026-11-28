<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Client-Side HTTP/2 (Preview)

@@@ warning
Client-Side HTTP/2 support in pekko-http is currently available as a preview.
This means it is ready to be evaluated, but the APIs and behavior are likely to change.
@@@

@@@ note
It is recommended to first read the @ref[Implications of the streaming nature of Request/Response Entities](../implications-of-streaming-http-entity.md) 
and @ref[Host-Level Client-Side API](./host-level.md) sections, as they explain the underlying full-stack streaming 
concepts, tuning the client settings and HTTPS context and how to handle the Request-Response Cycle, which may be 
unexpected when coming from a background with non-"streaming first" HTTP Clients.
@@@

Apache Pekko HTTP's HTTP/2 client disables [server push](https://www.rfc-editor.org/rfc/rfc9113.html#section-8.4) on each connection (`SETTINGS_ENABLE_PUSH = 0`).

## Create the client 

There are three mechanisms for a client to establish an HTTP/2 connection. Apache Pekko HTTP supports:

 - HTTP/2 over TLS 
 - HTTP/2 over a plain TCP connection ("h2c with Prior Knowledge")

Apache Pekko HTTP doesn't support:

 - HTTP `Upgrade` mechanism

### HTTP/2 over TLS

To create a client, use the `Http()` fluent API to connect and use the `http2()` creator:

Scala
:   @@snip[Http2Spec.scala](/docs/src/test/scala/docs/http/scaladsl/Http2Spec.scala) { #http2Client }

Java
:   @@snip[Http2Test.java](/docs/src/test/java/docs/http/javadsl/Http2Test.java) { #http2Client }

HTTP/2 over TLS needs [Application-Layer Protocol Negotiation (ALPN)](https://en.wikipedia.org/wiki/Application-Layer_Protocol_Negotiation)
to negotiate whether both client and server support HTTP/2.

Apache Pekko HTTP does not currently support protocol negotiation to fall back to HTTP/1.1 for this API. When the server does not support HTTP/2, the stream will fail.
### h2c with Prior Knowledge

The other option is to connect and start communicating in HTTP/2 immediately. You must know beforehand the target server
supports HTTP/2 over a plain TCP connection. For this reason this approach is known as h2c with
[Prior Knowledge](https://httpwg.org/specs/rfc7540.html#known-http) of HTTP/2 support.

To create a client, use the `Http()` fluent API to connect and use the `http2WithPriorKnowledge()` creator:

Scala
:   @@snip[Http2Spec.scala](/docs/src/test/scala/docs/http/scaladsl/Http2Spec.scala) { #http2ClientWithPriorKnowledge }

Java
:   @@snip[Http2Test.java](/docs/src/test/java/docs/http/javadsl/Http2Test.java) { #http2ClientWithPriorKnowledge }

### HTTP Upgrade mechanism

The Apache Pekko HTTP client doesn't support HTTP/1 to HTTP/2 negotiation over plaintext using the `Upgrade` mechanism.

If the HTTP/2 connection reaches end-of-input normally while a delivered response entity is still open, consuming
that entity fails with a connection-aborted `IllegalStateException`. Receiving response headers does not guarantee
that the full response body is available to the application. This also applies when the peer has already ended the
response stream but some of its data is still buffered and has not reached the application when the connection closes.

This behavior applies only to HTTP/2 client response entities, not to HTTP/2 server request entities.

## Limiting managed persistent connection lifetime

Managed persistent HTTP/2 clients can periodically retire long-lived connections so that later requests establish
fresh connections. This is useful when clients would otherwise remain pinned to the same server instances after
a scale-out or rolling deployment.

Configure the maximum age and optional jitter under the HTTP/2 client settings:

```
pekko.http.client.http2.persistent-connection-max-age = 10m
pekko.http.client.http2.persistent-connection-max-age-jitter = 0.1
```

The setting applies to `managedPersistentHttp2()` and `managedPersistentHttp2WithPriorKnowledge()`. Each
connection gets an independently jittered age; with the default jitter of `0.1`, retirement happens between 90%
and 110% of the configured maximum age. Set the jitter to `0` to disable it.

Retirement is currently **break-before-make**. When a connection reaches its age, the managed client stops
assigning new requests to it and lets requests already in flight drain before closing the connection. New requests
are backpressured until the old connection disconnects, then a replacement connection is established. As a result,
a retiring connection can add its drain time plus TCP/TLS/HTTP2 setup time to new-request latency.

The existing `completion-timeout` bounds the drain period. If that timeout expires, the old connection is closed
even when requests are still in flight, terminating long-lived requests such as streaming responses.

The default maximum age is `infinite`, which disables age-based retirement. The settings can also be changed
programmatically with `Http2ClientSettings.withPersistentConnectionMaxAge` and
`Http2ClientSettings.withPersistentConnectionMaxAgeJitter`.

With either managed persistent HTTP/2 client builder, completing the request source while a connection is
being established or waiting to reconnect leaves a final buffered, not-yet-dispatched request pending.
The client can send that request if a connection is established within the configured retry policy.
This does not replay requests already dispatched to a connection.

## Request-response ordering

For HTTP/2 connections the responses are not guaranteed to arrive in the same order that the requests were emitted to
the server, for example a request with a quickly available response may outrun a previous request that the server is
slower to respond to. For HTTP/2 it is therefore important to have a way to correlate the response with the request
it was made for. This can be achieved through a @apidoc[RequestResponseAssociation] set on the request, Apache Pekko HTTP will pass
such association objects on to the response.

In this sample the built-in @scala[`org.apache.pekko.http.scaladsl.model.ResponsePromise`]@java[`org.apache.pekko.http.javadsl.model.ResponseFuture`] `RequestResponseAssociation`  is used to return
a @scala[`Future`]@java[`CompletionStage`] for the response:

Scala
:  @@snip [HttpClientOutgoingConnection.scala](/docs/src/test/scala/docs/http/scaladsl/Http2ClientApp.scala) { #response-future-association }

Java

:  @@snip [HttpClientExampleDocTest.java](/docs/src/test/java/docs/http/javadsl/Http2ClientApp.java) { #response-future-association }

Connection-specific headers set on the model are dropped with a warning when sent over HTTP/2.
