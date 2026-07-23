# handleWebSocketMessages

@@@ div { .group-scala }

## Signature

@@signature [WebSocketDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/WebSocketDirectives.scala) { #handleWebSocketMessages }

@@@

## Description

The directive first checks if the request was a valid WebSocket handshake request and if yes, it completes the request
with the passed handler. Otherwise, the request is rejected with an @apidoc[ExpectedWebSocketRequestRejection$].

The overload that accepts a `shouldCompress` filter can select compression separately for each outbound message after
`permessage-deflate` is negotiated. It is evaluated synchronously once for each outbound text or binary message, and
its result applies to every fragment of that message. The filter is not invoked for control frames or when compression
was not negotiated. It should be fast and non-blocking; throwing from it fails the WebSocket stream.

For a streamed message, the complete payload and its final size are not available when the filter is evaluated.
Applications that select compression based on message size therefore need an explicit policy for streamed messages.

WebSocket subprotocols offered in the `Sec-WebSocket-Protocol` header of the request are ignored. If you want to
support several protocols use the @ref[handleWebSocketMessagesForProtocol](handleWebSocketMessagesForProtocol.md) directive, instead.

For more information about the WebSocket support, see @ref[Server-Side WebSocket Support](../../../server-side/websocket-support.md).

## Example

Scala
:  @@snip [WebSocketDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/WebSocketDirectivesExamplesSpec.scala) { #greeter-service }

Java
:  @@snip [WebSocketDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/WebSocketDirectivesExamplesTest.java) { #handleWebSocketMessages }
