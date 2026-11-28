<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# query

Matches requests with HTTP method `QUERY` (RFC 10008).

@@@ div { .group-scala }

## Signature

@@signature [MethodDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/MethodDirectives.scala) { #query }

@@@

## Description

This directive filters the incoming request by its HTTP method. Only requests with
method `QUERY` are passed on to the inner route. All others are rejected with a
@apidoc[MethodRejection], which is translated into a `405 Method Not Allowed` response
by the default @ref[RejectionHandler](../../rejections.md#the-rejectionhandler).

The `QUERY` method is defined in [RFC 10008](https://www.rfc-editor.org/rfc/rfc10008).
It is a safe and idempotent method that requests the target resource to process the
enclosed content and respond with the result. Unlike `GET`, the `QUERY` method expects
a request body containing the query payload.

For date-based revalidation with `If-Modified-Since`, use
@ref[`conditional`](../cache-condition-directives/conditional.md) after the application has
selected the query result. Supply the last-modified time of that result, not of the target URI
alone. When the result is unchanged, the directive can return a bodyless `304 Not Modified`,
as shown in [RFC 10008, Appendix A.5](https://www.rfc-editor.org/rfc/rfc10008.html#appendix-A.5).

## Example

Scala
:  @@snip [MethodDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/MethodDirectivesExamplesSpec.scala) { #query-method }

Java
:  @@snip [MethodDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/MethodDirectivesExamplesTest.java) { #query }
