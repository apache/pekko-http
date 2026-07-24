# handleWebSocketMessagesForOptionalProtocol

@@@ div { .group-scala }

## Signature

@@signature [WebSocketDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/WebSocketDirectives.scala) { #handleWebSocketMessagesForOptionalProtocol }

@@@

## Description

Handles WebSocket requests with the given handler and rejects other requests with an
@apidoc[ExpectedWebSocketRequestRejection$].

If the `subprotocol` parameter is @scala[None]@java[@javadoc:[empty](java.util.Optional#empty())] any WebSocket request is accepted. If the `subprotocol` parameter is
@scala[`Some(protocol)`]@java[a non-empty @javadoc:[Optional](java.util.Optional)] a WebSocket request is only accepted if the list of subprotocols supported by the client (as
announced in the WebSocket request) @scala[contains `protocol`]@java[matches the contained subprotocol]. If the client did not offer the protocol in question
the request is rejected with an @apidoc[UnsupportedWebSocketSubprotocolRejection].

The overload that accepts a `shouldCompress` filter can select compression separately for each outbound message after
`permessage-deflate` is negotiated. It is evaluated synchronously once for each outbound text or binary message, and
its result applies to every fragment of that message. The filter is not invoked for control frames or when compression
was not negotiated. It should be fast and non-blocking; throwing from it fails the WebSocket stream.

For a streamed message, the complete payload and its final size are not available when the filter is evaluated.
Applications that select compression based on message size therefore need an explicit policy for streamed messages.

To support several subprotocols you may chain several `handleWebSocketMessagesForOptionalProtocol` routes.

The `handleWebSocketMessagesForOptionalProtocol` directive is used as a building block for @ref[WebSocket Directives](index.md) to handle websocket messages.

For more information about the WebSocket support, see @ref[Server-Side WebSocket Support](../../../server-side/websocket-support.md).
