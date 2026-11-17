<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# extractScheme

@@@ div { .group-scala }

## Signature

@@signature [SchemeDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/SchemeDirectives.scala) { #extractScheme }

@@@

## Description

Extracts the Uri scheme (i.e. "`http`", "`https`", etc.) for an incoming request.

For rejecting a request if it doesn't match a specified scheme name, see the @ref[scheme](scheme.md) directive.

## Example

Scala
:  @@snip [SchemeDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/SchemeDirectivesExamplesSpec.scala) { #example-1 }

Java
:  @@snip [SchemeDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/SchemeDirectivesExamplesTest.java) { #extractScheme }
