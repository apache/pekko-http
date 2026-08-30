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

## Create the client 

There are several mechanisms for a client to establish an HTTP/2 connection. Apache Pekko HTTP supports:

 - HTTP/2 over TLS 
 - HTTP/2 over TLS with ALPN fallback to HTTP/1.1
 - HTTP/2 over a plain TCP connection ("h2c with prior knowledge")

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

`http2()` offers only `h2` in that handshake, so when the server does not support HTTP/2 the stream will fail. Use
`http2WithFallback()` if you need the connection to survive that case.

### HTTP/2 over TLS with fallback to HTTP/1.1

@@@ warning
`http2WithFallback()` is available as a preview. This means it is ready to be evaluated, but the API and behavior
are likely to change.
@@@

`http2WithFallback()` offers both `h2` and `http/1.1` in the ALPN handshake and runs whichever protocol the server
selected. A server that speaks HTTP/2 gets an HTTP/2 connection; a server that does not - including one that ignores
ALPN entirely - gets an HTTP/1.1 connection instead of a failed stream:

Scala
:   @@snip[Http2Spec.scala](/docs/src/test/scala/docs/http/scaladsl/Http2Spec.scala) { #http2ClientWithFallback }

Java
:   @@snip[Http2Test.java](/docs/src/test/java/docs/http/javadsl/Http2Test.java) { #http2ClientWithFallback }

Because the protocol is only known once the connection is up, requests should carry a @apidoc[RequestResponseAssociation]
as described in @ref[Request-response ordering](#request-response-ordering) - the flow may end up running HTTP/2, where
responses are not guaranteed to arrive in request order.

### h2c with prior knowledge

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
