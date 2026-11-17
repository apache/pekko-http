<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# mapUnmatchedPath

@@@ div { .group-scala }

## Signature

@@signature [BasicDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/BasicDirectives.scala) { #mapUnmatchedPath }

@@@

## Description

Transforms the unmatchedPath field of the request context for inner routes.

The `mapUnmatchedPath` directive is used as a building block for writing @ref[Custom Directives](../custom-directives.md). You can use it
for implementing custom path matching directives.

Use `extractUnmatchedPath` for extracting the current value of the unmatched path.

## Example

Scala
:  @@snip [BasicDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/BasicDirectivesExamplesSpec.scala) { #mapUnmatchedPath-example }

Java
:  @@snip [BasicDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/BasicDirectivesExamplesTest.java) { #mapUnmatchedPath }
