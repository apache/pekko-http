<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# encodeResponse

@@@ div { .group-scala }

## Signature

@@signature [CodingDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CodingDirectives.scala) { #encodeResponse }

@@@

## Description

Encodes the response with the encoding that is requested by the client via the `Accept-Encoding` header or rejects the request with an `UnacceptedResponseEncodingRejection(supportedEncodings)`.

The response encoding is determined by the rules specified in [RFC 9110 §12.5.3](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3).

If the `Accept-Encoding` header is missing or empty or specifies an encoding other than identity, gzip or deflate then no encoding is used.

When the selected representation can be encoded, the directive also adds `Accept-Encoding` to
[`Vary`](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.5), including for an unencoded response
or when the request did not include the selecting header. Existing `Vary` fields are preserved. It
does not add the field to an already encoded response. A standalone `304 Not Modified` does not
describe the corresponding `200 OK` here, so the directive preserves any existing `Vary` without adding
to it. The application creating the `304` must include the `Vary` it would send on the corresponding
`200` ([RFC 9110 §15.4.5](https://www.rfc-editor.org/rfc/rfc9110.html#section-15.4.5)).

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponse }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponse }
