<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# decodeRequestWith

@@@ div { .group-scala }

## Signature

@@signature [CodingDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CodingDirectives.scala) { #decodeRequestWith }

@@@

## Description

Decodes the incoming request if it is encoded with one of the given encoders.
If the request encoding doesn't match one of the given encoders the request is rejected with an @apidoc[UnsupportedRequestEncodingRejection]. If no decoders are given the default encoders (`Gzip`, `Deflate`, `NoCoding`) are used.
If the request entity after decoding exceeds `pekko.http.routing.decode-max-size` the stream fails with an
@scala[@scaladoc[EntityStreamSizeException](org.apache.pekko.http.scaladsl.model.EntityStreamSizeException)]@java[@javadoc[EntityStreamSizeException](org.apache.pekko.http.scaladsl.model.EntityStreamSizeException)].


To opt a route into a custom content coding, create a Scala coder with `Coders.fromFlows` and pass it
to `decodeRequestWith`. In Java, create a `FlowCoder` with `FlowCoder.create` and use
`decodeRequestWithFlowCoder`. Neither extends the built-in default coders. The decoder factory is
called for each stream materialization and must honor the requested maximum output chunk size. A custom
coder decodes a single request content coding, not a stack of codings.

## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #decodeRequestWith }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #decodeRequestWith }
