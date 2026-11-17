<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# authenticateOrRejectWithChallenge

@@@ div { .group-scala }
## Signature

@@signature [SecurityDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/SecurityDirectives.scala) { #AuthenticationResult }

@@signature [SecurityDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/SecurityDirectives.scala) { #authenticateOrRejectWithChallenge }

@@@

## Description

Lifts an authenticator function into a directive.

This directive allows implementing the low level challenge-response type of authentication that some services may require.

More details about challenge-response authentication are available in the [RFC 2617](https://tools.ietf.org/html/rfc2617), [RFC 7616](https://tools.ietf.org/html/rfc7616) and [RFC 7617](https://tools.ietf.org/html/rfc7617).

## Example

Scala
:  @@snip [SecurityDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/SecurityDirectivesExamplesSpec.scala) { #authenticateOrRejectWithChallenge-0 }

Java
:  @@snip [SecurityDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/SecurityDirectivesExamplesTest.java) { #authenticateOrRejectWithChallenge }
