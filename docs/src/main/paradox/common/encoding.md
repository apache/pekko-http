<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# Encoding / Decoding

The [`Content-Encoding` header](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.4) identifies the content
codings applied to a representation. Most commonly, these are compression algorithms.

Currently, Apache Pekko HTTP supports the compression and decompression of HTTP requests and responses with the `gzip`,
`deflate` or `zstd` encodings.

`zstd` support needs the optional `com.github.luben:zstd-jni` dependency (1.5.7-25 or later) on the classpath, so it is
not one of the default encodings and has to be requested explicitly, for example with
@ref[encodeResponseWith](../routing-dsl/directives/coding-directives/encodeResponseWith.md) and
@ref[decodeRequestWith](../routing-dsl/directives/coding-directives/decodeRequestWith.md). As required by
[RFC 9659](https://www.rfc-editor.org/rfc/rfc9659), `zstd` does not compress with a window larger than 8 MB, and by default
does not decode messages that need a larger window.

The `dcz` encoding of [RFC 9842](https://www.rfc-editor.org/rfc/rfc9842) compresses with a dictionary that both sides have,
see @ref[Compression dictionaries](#compression-dictionaries). It needs `zstd-jni` as well, 1.5.7-26 or later. The `dcb` encoding of the same
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
header. Coding names are matched case-insensitively. A response with an unsupported encoding or more than one coding
is returned unchanged, with its `Content-Encoding` header kept:

Scala
:   @@snip [HttpClientDecodingExampleSpec.scala](/docs/src/test/scala/docs/http/scaladsl/HttpClientDecodingExampleSpec.scala) { #single-request-decoding-example }

Java
:   @@snip [HttpClientDecodingExampleTest.java](/docs/src/test/java/docs/http/javadsl/HttpClientDecodingExampleTest.java) { #single-request-decoding-example }

To decode one layer explicitly, call @scala[`Coders.Gzip.decodeMessage(response)`]@java[`Coder.Gzip.decodeMessage(response)`]
or use another built-in coder. The chosen coder must match the last coding listed in `Content-Encoding`,
which is the outermost layer. Decoding removes that coding only. If another coding remains, the body is still
encoded and the remaining `Content-Encoding` values are preserved, whether they arrived in one field or several.
If the chosen coder does not match, the response is returned unchanged.

## Compression dictionaries

A dictionary, for example a previous version of a resource, makes small or frequently changing resources compress much better.
@scala[`Coders.Dcz`]@java[`DictionaryCoder.create`] creates a coder for a dictionary. Creating one hashes the dictionary, so
create one per dictionary and reuse it.

On the server side, mark a response as a dictionary with the `Use-As-Dictionary` header, and encode later responses with
@ref[encodeResponseWithDictionary](../routing-dsl/directives/coding-directives/encodeResponseWithDictionary.md). The application
decides how dictionaries are stored. pekko-http does not limit which responses are encoded, so do not use a dictionary for a
response that mixes secrets with content that an attacker controls, and only use it over HTTPS as RFC 9842 requires.

On the client side, there is no dictionary store. To use a dictionary, keep it yourself, send its hash in an
`Available-Dictionary` header with `dcz` in the `Accept-Encoding` header, and decode the response with the coder as shown above.
A response compressed with a different dictionary fails to decode.

As RFC 9842 requires, the dictionary is always used as raw content, also if it starts with the magic number of the zstd
dictionary format. This needs `zstd-jni` 1.5.7-26 or later.
