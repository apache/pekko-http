# extractDirectClientIP

@@@ div { .group-scala }

## Signature

@@signature [MiscDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/MiscDirectives.scala) { #extractDirectClientIP }

@@@

## Description

Provides the value of the @apidoc[AttributeKeys.remoteAddress](AttributeKeys$) @ref[attribute](../../../common/http-model.md#attributes), which is the address of the peer of the connection the request arrived on. It requires the `pekko.http.server.remote-address-attribute` setting to be `on` and provides `RemoteAddress.Unknown` otherwise.

Unlike @ref[extractClientIP](extractClientIP.md), this directive ignores the `X-Forwarded-For` and `X-Real-IP` headers, so the address it provides cannot be chosen by the client. Note that behind a proxy this is the address of the proxy and not of the client that the proxy forwarded the request for.

## Example

Scala
:  @@snip [MiscDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/MiscDirectivesExamplesSpec.scala) { #extractDirectClientIP-example }

Java
:  @@snip [MiscDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/MiscDirectivesExamplesTest.java) { #extractDirectClientIPExample }
