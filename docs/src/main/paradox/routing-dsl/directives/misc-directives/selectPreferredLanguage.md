<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# selectPreferredLanguage

@@@ div { .group-scala }

## Signature

@@signature [MiscDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/MiscDirectives.scala) { #selectPreferredLanguage }

@@@

## Description

Uses the request's [`Accept-Language` header](https://www.rfc-editor.org/rfc/rfc9110.html#section-12.5.4) to choose
among the given language alternatives.

When preferred alternatives tie, the directive chooses the first of them in argument order. If no alternative is
acceptable, it falls back to the first argument.

## Example

Scala
:  @@snip [MiscDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/MiscDirectivesExamplesSpec.scala) { #selectPreferredLanguage-example }

Java
:  @@snip [MiscDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/MiscDirectivesExamplesTest.java) { #selectPreferredLanguage }
