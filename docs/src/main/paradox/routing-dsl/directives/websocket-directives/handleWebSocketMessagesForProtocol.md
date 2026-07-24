# handleWebSocketMessagesForProtocol

@@@ div { .group-scala }

## Signature

@@signature [WebSocketDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/WebSocketDirectives.scala) { #handleWebSocketMessagesForProtocol }

@@@

## Description

Handles WebSocket requests with the given handler if the given subprotocol is offered in the `Sec-WebSocket-Protocol`
header of the request and rejects other requests with an @apidoc[ExpectedWebSocketRequestRejection$] or an
@apidoc[UnsupportedWebSocketSubprotocolRejection].

The directive first checks if the request was a valid WebSocket handshake request and if the request offers the passed
subprotocol name. If yes, the directive completes the request with the passed handler. Otherwise, the request is
either rejected with an @apidoc[ExpectedWebSocketRequestRejection$] or an @apidoc[UnsupportedWebSocketSubprotocolRejection].

The overload that accepts a `shouldCompress` filter can select compression separately for each outbound message after
`permessage-deflate` is negotiated. It is evaluated synchronously once for each outbound text or binary message, and
its result applies to every fragment of that message. The filter is not invoked for control frames or when compression
was not negotiated. It should be fast and non-blocking; throwing from it fails the WebSocket stream.

For a streamed message, the complete payload and its final size are not available when the filter is evaluated.
Applications that select compression based on message size therefore need an explicit policy for streamed messages.

To support several subprotocols, for example at the same path, several instances of `handleWebSocketMessagesForProtocol` can
be chained using `~` as you can see in the below example.

For more information about the WebSocket support, see @ref[Server-Side WebSocket Support](../../../server-side/websocket-support.md).

## Example

Scala
:  @@snip [WebSocketDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/WebSocketDirectivesExamplesSpec.scala) { #handle-multiple-protocols }

Java
:  @@snip [WebSocketDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/WebSocketDirectivesExamplesTest.java) { #handleWebSocketMessagesForProtocol }
