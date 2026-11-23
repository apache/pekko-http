<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# setCookie

@@@ div { .group-scala }

## Signature

@@signature [CookieDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CookieDirectives.scala) { #setCookie }

@@@

## Description

Adds a header to the response to request the update of the cookie with the given name on the client.

Use `withPartitioned(true)` on an `HttpCookie` to emit the `Partitioned` attribute. Browsers require
`Secure` for partitioned cookies, so also set `withSecure(true)`. Pekko HTTP models and renders the
attribute; it does not implement browser-side cookie storage or partitioning. Parsing a bare
`Partitioned` attribute sets `partitioned` instead of storing it as a generic extension.
Disabling the typed attribute does not change an existing raw extension set with `withExtension`.
If the raw extension is exactly the bare `Partitioned` attribute (ignoring case) and the typed
attribute is also enabled, it is rendered once using the raw spelling.

Use the @ref[deleteCookie](deleteCookie.md) directive to delete a cookie.

## Example

Scala
:  @@snip [CookieDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/CookieDirectivesExamplesSpec.scala) { #setCookie }

Java
:  @@snip [CookieDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/CookieDirectivesExamplesTest.java) { #setCookie }
