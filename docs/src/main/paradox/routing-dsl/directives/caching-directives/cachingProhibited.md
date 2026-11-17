<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# cachingProhibited

@@@ div { .group-scala }

## Signature

@@signature [CachingDirectives.scala](/http-caching/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CachingDirectives.scala) { #cachingProhibited }

@@@

## Description

This directive is used to filter out requests that forbid caching. It is used as a building block of the @ref[cache](cache.md) directive to prevent caching if the client requests so.

## Example

Scala
:  @@snip [HeaderDirectivesExamplesSpec.scala]($root$/src/test/scala/docs/http/scaladsl/server/directives/CachingDirectivesExamplesSpec.scala) { #caching-prohibited }

Java
:  @@snip [CachingDirectivesExamplesTest.java]($root$/src/test/java/docs/http/javadsl/server/directives/CachingDirectivesExamplesTest.java) { #caching-prohibited }
