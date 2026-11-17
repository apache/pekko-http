# encodeResponseWith

@@@ div { .group-scala }

## Signature

@@signature [CodingDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CodingDirectives.scala) { #encodeResponseWith }

@@@

## Description

Encodes the response with the encoding that is requested by the client via the `Accept-Encoding` if it is among the provided encoders or rejects the request with an `UnacceptedResponseEncodingRejection(supportedEncodings)`.

The response encoding is determined by the rules specified in [RFC 9110 §12.5.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3).

If the `Accept-Encoding` header is missing then the response is encoded using the `first` encoder.

An explicitly empty `Accept-Encoding` header accepts no content coding. Include `NoCoding` among the offered encoders to return an unencoded response. If `NoCoding` is not offered, the directive rejects with `UnacceptedResponseEncodingRejection`; the default rejection handler returns `406 Not Acceptable`. This does not affect `encodeResponse`, which includes `NoCoding` by default.

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponseWith }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponseWith }
