<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  license agreements; and to You under the Apache License, version 2.0:

    https://www.apache.org/licenses/LICENSE-2.0

  This file is part of the Apache Pekko project, which was derived from Akka.
-->

# conditional

@@@ div { .group-scala }

## Signature

@@signature [CacheConditionDirectives.scala](/http/src/main/scala/org/apache/pekko/http/scaladsl/server/directives/CacheConditionDirectives.scala) { #conditional }

@@@

## Description

Wraps its inner route with support for [HTTP conditional requests](https://www.rfc-editor.org/rfc/rfc9110.html#section-13).

Depending on the given `eTag` and `lastModified` values this directive immediately responds with
`304 Not Modified` or `412 Precondition Failed` (without calling its inner route) if the request comes with the
respective conditional headers. Otherwise the request is simply passed on to its inner route.

Evaluation order is based on [RFC 9110, Section 13.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.2.2).
Preconditions that need an unavailable validator are skipped.

The directive adds the supplied @apidoc[ETag] and `Last-Modified` as default headers on a `304 Not Modified` response
it produces and on responses from the inner route.

Since this directive requires the @apidoc[EntityTag] and `lastModified` time stamp for the resource as concrete arguments
it is usually used quite deep down in the route structure (i.e. close to the leaf-level), where the exact resource
targeted by the request has already been established and the respective ETag/Last-Modified values can be determined.

The @ref[FileAndResourceDirectives](../file-and-resource-directives/index.md) internally use the `conditional` directive for ETag and Last-Modified support
(if the `pekko.http.routing.file-get-conditional` setting is enabled).
