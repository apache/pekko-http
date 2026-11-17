<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# optionalAttribute

@@@ div { .group-scala }

## Signature

@@signature [AttributeDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/AttributeDirectives.scala) { #optionalAttribute }

@@@

## Description

Optionally extracts the value of the request @ref[attribute](../../../common/http-model.md#attributes) with the given key.

The `optionalAttribute` directive is similar to the @ref[attribute](attribute.md) directive but always extracts
an @scala[`Option`]@java[`Optional`] value instead of rejecting the request if no matching attribute could be found.

## Example

Scala
:  @@snip [AttributeDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/AttributeDirectivesExamplesSpec.scala) { #optionalAttribute }

Java
:  @@snip [AttributeDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/AttributeDirectivesExamplesTest.java) { #optionalAttribute }
