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

Apache Pekko HTTP clients support two ways to start an HTTP/2 connection:

 - HTTP/2 over TLS
 - Cleartext HTTP/2 with prior knowledge

The client does not implement the legacy HTTP/1.1 `h2c` Upgrade mechanism, which
[RFC 9113, Section 3.1](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.1) deprecates.

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

With [prior knowledge](https://www.rfc-editor.org/rfc/rfc9113.html#section-3.3), the client starts speaking HTTP/2
immediately. It must already know that the target server supports HTTP/2 over a plain TCP connection. No HTTP/1.1
Upgrade request is sent.

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

## Observing 103 Early Hints

The HTTP/2 client normally emits only the final response. To also observe [103 Early Hints](https://www.rfc-editor.org/rfc/rfc8297.html#section-2),
materialize a bounded `EarlyHintsObserver` source and attach the observer to the request as a local attribute:

@@@ div { .group-scala }
```scala
import org.apache.pekko.http.scaladsl.model.{ EarlyHintsObserver, HttpRequest }
import org.apache.pekko.stream.scaladsl.Sink

val observer = EarlyHintsObserver.source(8).to(Sink.foreach(hint => println(hint.headers))).run()
val request = HttpRequest(uri = "/").addAttribute(EarlyHintsObserver.attributeKey, observer)
// sendOnHttp2 sends the request and returns a Future[HttpResponse].
val response = sendOnHttp2(request).andThen { case _ => observer.complete() }
```
@@@

@@@ div { .group-java }
```java
import org.apache.pekko.http.javadsl.model.EarlyHintsObserver;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.stream.javadsl.Sink;

EarlyHintsObserver observer = EarlyHintsObserver.createSource(8)
    .to(Sink.foreach(hint -> System.out.println(hint.getHeaders()))).run(system);
HttpRequest request = HttpRequest.GET("/").addAttribute(EarlyHintsObserver.getAttributeKey(), observer);
// sendOnHttp2 sends the request and returns a CompletionStage<HttpResponse>.
var response = sendOnHttp2.apply(request).whenComplete((ignored, error) -> observer.complete());
```
@@@

Each notification preserves the received field order, including repeated fields. The client offers a separate
notification for each 103, even if successive responses contain the same fields. Use one observer per request
to keep the notifications associated with that request.

Notifications are offered before the final response on a best-effort basis. A full or closed observer drops
new hints. The observer processes them independently, so it may still be draining when the final response
arrives. The buffer size counts notifications; HTTP/2 header parsing applies its normal size limits. The
application must still consume the normal response stream.

The application owns the observer's lifetime: calling `complete()` drains already queued hints, and cancelling
the observer source discards them. Calling `complete()` again is safe. Neither action ends the request.
The client does not fetch the links or merge hints into the final response.

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
