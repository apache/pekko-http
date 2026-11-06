# encodeResponseWithDictionary

@@@ div { .group-scala }

## Signature

@@signature [CodingDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CodingDirectives.scala) { #encodeResponseWithDictionary }

@@@

## Description

Encodes the response with the `dcz` encoding of [RFC 9842](https://www.rfc-editor.org/rfc/rfc9842) if the `Accept-Encoding` header lists `dcz` and the given lookup finds the dictionary with the hash in the `Available-Dictionary` header.
The application decides how dictionaries are stored, see @ref[Compression dictionaries](../../../common/encoding.md#compression-dictionaries).

Otherwise the response is encoded like in @ref[encodeResponse](encodeResponse.md), or like in @ref[encodeResponseWith](encodeResponseWith.md) if encoders are given. If a dictionary was found but cannot be used for the response, and the client accepts none of the encoders, the response is not encoded instead of the request being rejected.

All responses get a `Vary: Accept-Encoding, Available-Dictionary` header, so that caches do not reuse a response for another dictionary.

As RFC 9842 requires, the dictionary is not used for a cross-origin request whose response cannot be read by the client, based on the `Sec-Fetch-Site`, `Sec-Fetch-Mode` and `Origin` request headers and the `Access-Control-Allow-Origin` response header. A directive that adds `Access-Control-Allow-Origin` has to be inside this one, otherwise cross-origin requests do not use the dictionary. RFC 9842 also requires HTTPS, which this directive cannot check as it may be behind a proxy that terminates TLS.

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponseWithDictionary }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponseWithDictionary }
