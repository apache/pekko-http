<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# scheme

@@@ div { .group-scala }

## Signature

@@signature [SchemeDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/SchemeDirectives.scala) { #scheme }

@@@

## Description

Rejects a request if its Uri scheme does not match a given one.

The `scheme` directive can be used to match requests by their Uri scheme, only passing
through requests that match the specified scheme and rejecting all others.

A typical use case for the `scheme` directive would be to reject requests coming in over
http instead of https, or to redirect such requests to the matching https URI with a
`MovedPermanently`.

For simply extracting the scheme name, see the @ref[extractScheme](extractScheme.md) directive.

## Example

Scala
:  @@snip [SchemeDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/SchemeDirectivesExamplesSpec.scala) { #example-2 }

Java
:  @@snip [SchemeDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/SchemeDirectivesExamplesTest.java) { #scheme }
