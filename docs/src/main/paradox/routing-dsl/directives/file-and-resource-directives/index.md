<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# FileAndResourceDirectives

Like the @ref[RouteDirectives](../route-directives/index.md) the @apidoc[FileAndResourceDirectives] are somewhat
special in pekko-http's routing DSL. Most other directives wrap an inner route and are therefore used as inner nodes of
the route tree. These directives, instead, are indeed instance of @scala[@scaladoc[Route](org.apache.pekko.http.scaladsl.server.index#Route=org.apache.pekko.http.scaladsl.server.RequestContext=%3Escala.concurrent.Future[org.apache.pekko.http.scaladsl.server.RouteResult])]@java[@javadoc[Route](org.apache.pekko.http.javadsl.server.Route)], i.e. **leaves** of the route tree that handle a
request themselves without passing it on to an inner route.

So in most cases the inner-most element of a route structure branch is one of the @ref[RouteDirectives](../route-directives/index.md) or
@apidoc[FileAndResourceDirectives].

## Content types

The file and resource directives pick the `Content-Type` of a response from the file name using a `ContentTypeResolver`.
@scala[It is passed implicitly, and defaults to `ContentTypeResolver.Default`]@java[By default they use the same
resolver as the Scala DSL's `ContentTypeResolver.Default`; overloads taking a `ContentTypeResolver` let you pass your
own], which looks up the file extension among the predefined media types.

To serve file types that Pekko HTTP does not predefine, or to override a predefined mapping, use
`ContentTypeResolver.withExtensions`. It looks up the given extensions first, ignoring case, and otherwise behaves
like the default, including for precompressed `.gz` files:

Scala
:  @@snip [FileAndResourceDirectivesExamplesSpec.scala](/docs/src/test/scala/docs/http/scaladsl/server/directives/FileAndResourceDirectivesExamplesSpec.scala) { #content-type-resolver-with-extensions }

Java
:  @@snip [FileAndResourceDirectivesExamplesTest.java](/docs/src/test/java/docs/http/javadsl/server/directives/FileAndResourceDirectivesExamplesTest.java) { #content-type-resolver-with-extensions }

@@toc { depth=1 }

@@@ index

* [getFromBrowseableDirectories](getFromBrowseableDirectories.md)
* [getFromBrowseableDirectory](getFromBrowseableDirectory.md)
* [getFromDirectory](getFromDirectory.md)
* [getFromFile](getFromFile.md)
* [getFromResource](getFromResource.md)
* [getFromResourceDirectory](getFromResourceDirectory.md)
* [listDirectoryContents](listDirectoryContents.md)

@@@
