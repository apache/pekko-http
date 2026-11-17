<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# deleteCookie

@@@ div { .group-scala }

## Signature

@@signature [CookieDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CookieDirectives.scala) { #deleteCookie }

@@@

## Description

Adds a header to the response to request the removal of the cookie with the given name on the client.

Use the @ref[setCookie](setCookie.md) directive to update a cookie.

## Example

Scala
:  @@snip [CookieDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CookieDirectivesExamplesSpec.scala) { #deleteCookie }

Java
:  @@snip [CookieDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CookieDirectivesExamplesTest.java) { #deleteCookie }
