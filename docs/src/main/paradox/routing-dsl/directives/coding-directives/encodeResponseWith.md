<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

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

To opt a route into a custom content coding, create a Scala coder with `Coders.fromFlows` and pass it
to `encodeResponseWith`. In Java, create a `FlowCoder` with `FlowCoder.create` and use
`encodeResponseWithFlowCoder`. This does not change the default coders. The encoder flow is materialized once per
encoded response and must not share codec state between materializations.

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponseWith }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponseWith }

## Route-local minimum size

To opt a route into a minimum size for gzip, use a custom filter. The filter checks the original response entity's
known length in bytes, before encoding. The examples keep the usual eligibility checks and only gzip an entity of
at least 1024 bytes. They leave an entity of unknown length, such as a typical chunked response, unchanged without
buffering the stream.

This is an application policy, not a new default. These examples assume the client accepts gzip and permits an
unencoded response. If the client excludes the identity coding, do not rely on a size filter alone to select an
acceptable response: the filter sees the response, not the request's `Accept-Encoding`.

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponseWithMinimumSize }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponseWithMinimumSize }
