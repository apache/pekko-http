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

Uses the request's [`Accept-Encoding` header](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.3) to choose
from `identity` (no coding), `gzip`, and `deflate`.

If the header is absent or empty, the directive leaves the response unchanged. It also does so if no supported content
coding is acceptable, unless the request excludes `identity`. If no available option is acceptable, it rejects the
request with an `UnacceptedResponseEncodingRejection(supportedEncodings)`.

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #encodeResponse }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #encodeResponse }
