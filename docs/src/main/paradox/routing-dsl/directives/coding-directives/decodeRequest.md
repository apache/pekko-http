<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# decodeRequest

@@@ div { .group-scala }

## Signature

@@signature [CodingDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CodingDirectives.scala) { #decodeRequest }

@@@

## Description

Decompresses the incoming request if it is `gzip` or `deflate` compressed. Uncompressed requests are passed through untouched.
If the request encoded with another encoding the request is rejected with an @apidoc[UnsupportedRequestEncodingRejection].
If the request entity after decoding exceeds `pekko.http.routing.decode-max-size` the stream fails with an
@scala[@scaladoc[EntityStreamSizeException](org.apache.pekko.http.scaladsl.model.EntityStreamSizeException)]@java[@javadoc[EntityStreamSizeException](org.apache.pekko.http.scaladsl.model.EntityStreamSizeException)].


## Example

Scala
:  @@snip [CodingDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CodingDirectivesExamplesSpec.scala) { #decodeRequest }

Java
:  @@snip [CodingDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CodingDirectivesExamplesTest.java) { #decodeRequest }
