<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# optionalHeaderValue

@@@ div { .group-scala }

## Signature

@@signature [HeaderDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/HeaderDirectives.scala) { #optionalHeaderValue }

@@@

## Description

Traverses the list of request headers with the specified function and extracts the first value the function returns
@scala[as `Some(value)`]@java[a non empty `Optional<T>`].

The `optionalHeaderValue` directive is similar to the @ref[headerValue](headerValue.md) directive but always extracts an @scala[`Option`]@java[`Optional`]
value instead of rejecting the request if no matching header could be found.

## Example

Scala
:  @@snip [HeaderDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/HeaderDirectivesExamplesSpec.scala) { #optionalHeaderValue-0 }

Java
:  @@snip [HeaderDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/HeaderDirectivesExamplesTest.java) { #optionalHeaderValue }
