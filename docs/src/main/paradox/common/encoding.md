# Encoding / Decoding

The [HTTP spec](https://tools.ietf.org/html/rfc7231#section-3.1.2.1) defines a `Content-Encoding` header, which signifies whether the entity body of an HTTP message is
"encoded" and, if so, by which algorithm. The only commonly used content encodings are compression algorithms.

Currently, Apache Pekko HTTP supports the compression and decompression of HTTP requests and responses with the `gzip`,
`deflate` or `zstd` encodings.

`zstd` support needs the optional `com.github.luben:zstd-jni` dependency (1.5.7-25 or later) on the classpath, so it is
not one of the default encodings and has to be requested explicitly, for example with
@ref[encodeResponseWith](../routing-dsl/directives/coding-directives/encodeResponseWith.md) and
@ref[decodeRequestWith](../routing-dsl/directives/coding-directives/decodeRequestWith.md). As required by
[RFC 9659](https://www.rfc-editor.org/rfc/rfc9659), `zstd` does not compress with a window larger than 8 MB, and by default
does not decode messages that need a larger window.

The `dcz` encoding of [RFC 9842](https://www.rfc-editor.org/rfc/rfc9842) compresses with a dictionary that both sides have,
see @ref[Compression dictionaries](#compression-dictionaries). It needs `zstd-jni` as well. The `dcb` encoding of the same
RFC is not supported, as it needs a Brotli implementation.

The core logic for this lives in the @scala[@scaladoc[org.apache.pekko.http.scaladsl.coding](org.apache.pekko.http.scaladsl.coding.index) package.]@java[@javadoc[org.apache.pekko.http.javadsl.coding.Coder](org.apache.pekko.http.javadsl.coding.Coder) enum class.]

## Server side

The support is not enabled automatically, but must be explicitly requested.
For enabling message encoding/decoding with @ref[Routing DSL](../routing-dsl/index.md) see the @ref[CodingDirectives](../routing-dsl/directives/coding-directives/index.md).

Usually, it suffices to surround routes that should support response encoding by the @ref[encodeResponse](../routing-dsl/directives/coding-directives/encodeResponse.md) directive,
and routes that should support request decoding by the @ref[decodeRequest](../routing-dsl/directives/coding-directives/decodeRequest.md) directive. Those directives will automatically
and transparently enable support for encodings and negotiating which encoding to use out of the default encodings supported. The set of predefined @apidoc[MediaTypes$] contains
a hint whether content of a given media type would benefit from compression.

## Client side

Responses are not decoded automatically on the client-side.

@scala[`Coders.decodeMessage`]@java[`Coder.decodeResponse`] decodes a response with the coder that matches its `Content-Encoding`
header. A response whose encoding is not supported by any of the given coders is returned unchanged, with its
`Content-Encoding` header kept:

Scala
:   @@snip [HttpClientDecodingExampleSpec.scala](/docs/src/test/scala/docs/http/scaladsl/HttpClientDecodingExampleSpec.scala) { #single-request-decoding-example }

Java
:   @@snip [HttpClientDecodingExampleTest.java](/docs/src/test/java/docs/http/javadsl/HttpClientDecodingExampleTest.java) { #single-request-decoding-example }

## Compression dictionaries

A dictionary, for example a previous version of a resource, makes small or frequently changing resources compress much better.
@scala[`Coders.Dcz`]@java[`DictionaryCoder.create`] creates a coder for a dictionary. Creating one hashes the dictionary, so
create one per dictionary and reuse it.

On the server side, mark a response as a dictionary with the @apidoc[Use-As-Dictionary] header, and encode later responses with
@ref[encodeResponseWithDictionary](../routing-dsl/directives/coding-directives/encodeResponseWithDictionary.md). The application
decides how dictionaries are stored. pekko-http does not limit which responses are encoded, so do not use a dictionary for a
response that mixes secrets with content that an attacker controls, and only use it over HTTPS as RFC 9842 requires.

On the client side, there is no dictionary store. To use a dictionary, keep it yourself, send its hash in an
@apidoc[Available-Dictionary] header with `dcz` in the `Accept-Encoding` header, and decode the response with the coder as shown above.
A response compressed with a different dictionary fails to decode.

A dictionary that starts with the zstd dictionary magic number (`0x37 0xA4 0x30 0xEC`) is rejected, as `zstd-jni` cannot load such a
dictionary as raw content, which RFC 9842 requires.
