<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# extractMatchedPath

@@@ div { .group-scala }

## Signature

@@signature [BasicDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/BasicDirectives.scala) { #extractMatchedPath }

@@@

## Description

Extracts the matched path from the request context.

The `extractMatchedPath` directive extracts the path that was already matched by any of the @ref[PathDirectives](../path-directives/index.md)
(or any custom ones that change the unmatched path field of the request context). You can use it for building directives
that use already matched part in their logic.

See also @ref[extractUnmatchedPath](extractUnmatchedPath.md) to see similar directive for unmatched path.

## Example

Scala
:  @@snip [BasicDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/BasicDirectivesExamplesSpec.scala) { #extractMatchedPath-example }

Java
:  @@snip [BasicDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/BasicDirectivesExamplesTest.java) { #extractMatchedPath }
