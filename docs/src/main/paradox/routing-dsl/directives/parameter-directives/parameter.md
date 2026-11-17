<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# parameter

## Description

Extracts a *query* parameter value from the @scala[request]@java[request and provides it to the inner route as a `String`].

@scala[In the Scala API, `parameter` is an alias for `parameters` and you can use both directives to extract any number of parameter values.]
For a detailed description about how to extract one or more parameters see @ref[parameters](parameters.md).

See @ref[When to use which parameter directive?](index.md#which-parameter-directive) to understand when to use which directive.

## Example

Scala
:  @@snip [ParameterDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/ParameterDirectivesExamplesSpec.scala) { #example-1 }

Java
:  @@snip [ParameterDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/ParameterDirectivesExamplesTest.java) { #parameter }
