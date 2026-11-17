<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# handleRejections

@@@ div { .group-scala }

## Signature

@@signature [ExecutionDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/ExecutionDirectives.scala) { #handleRejections }

@@@

## Description

Using this directive is an alternative to using a global implicitly defined `RejectionHandler` that
applies to the complete route.

See @ref[Rejections](../../rejections.md) for general information about options for handling rejections.

## Example

Scala
:  @@snip [ExecutionDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/ExecutionDirectivesExamplesSpec.scala) { #handleRejections }

Java
:  @@snip [ExecutionDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/ExecutionDirectivesExamplesTest.java) { #handleRejections }
