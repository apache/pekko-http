<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# alwaysCache

@@@ div { .group-scala }

## Signature

@@signature [CachingDirectives.scala](/http-caching/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CachingDirectives.scala) { #alwaysCache }

@@@

## Description

Like @ref[cache](cache.md) but disregards a `Cache-Control` request header.

## Example

Scala
:  @@snip [CachingDirectivesExamplesSpec.scala]($root$/src/test/scala/docs/http/scaladsl/server/directives/CachingDirectivesExamplesSpec.scala) { #always-cache }

Java
:  @@snip [CachingDirectivesExamplesTest.java]($root$/src/test/java/docs/http/javadsl/server/directives/CachingDirectivesExamplesTest.java) { #always-cache }
