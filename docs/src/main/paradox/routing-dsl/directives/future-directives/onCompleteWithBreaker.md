<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# onCompleteWithBreaker

@@@ div { .group-scala }

## Signature

@@signature [FutureDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/FutureDirectives.scala) { #onCompleteWithBreaker }

@@@

## Description

Evaluates its parameter of type @scala[`Future[T]`]@java[`CompletionStage<T>`] protecting it with the specified @apidoc[CircuitBreaker].
Refer to @extref[Circuit Breaker](pekko-docs:common/circuitbreaker.html) for a detailed description of this pattern.

If the @apidoc[CircuitBreaker] is open, the request is rejected with a @apidoc[CircuitBreakerOpenRejection].
Note that in this case the request's entity databytes stream is cancelled, and the connection is closed
as a consequence.

Otherwise, the same behaviour provided by @ref[onComplete](onComplete.md) is to be expected.

## Example

Scala
:  @@snip [FutureDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FutureDirectivesExamplesSpec.scala) { #onCompleteWithBreaker }

Java
:  @@snip [FutureDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FutureDirectivesExamplesTest.java) { #onCompleteWithBreaker }
