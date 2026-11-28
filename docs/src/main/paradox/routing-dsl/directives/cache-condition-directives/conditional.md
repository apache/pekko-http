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

Wraps its inner route with support for Conditional Requests as defined
by [https://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26](https://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26).

Depending on the given `eTag` and `lastModified` values this directive immediately responds with
`304 Not Modified` or `412 Precondition Failed` (without calling its inner route) if the request comes with the
respective conditional headers. Otherwise the request is simply passed on to its inner route.

The algorithm implemented by this directive closely follows what is defined in [this section](https://tools.ietf.org/html/draft-ietf-httpbis-p4-conditional-26#section-6) of the
[HTTPbis spec](https://datatracker.ietf.org/wg/httpbis/documents/).

All responses (the ones produces by this directive itself as well as the ones coming back from the inner route) are
augmented with respective @apidoc[ETag] and `Last-Modified` response headers.

Since this directive requires the @apidoc[EntityTag] and `lastModified` time stamp for the resource as concrete arguments
it is usually used quite deep down in the route structure (i.e. close to the leaf-level), where the exact resource
targeted by the request has already been established and the respective ETag/Last-Modified values can be determined.

The @ref[FileAndResourceDirectives](../file-and-resource-directives/index.md) internally use the `conditional` directive for ETag and Last-Modified support
(if the `pekko.http.routing.file-get-conditional` setting is enabled).

## Evaluating conditions outside a route

An application or framework that selects the representation itself can use `SelectedRepresentationConditions.evaluate`
from the Scala or Java model API. Call it after normal request checks, only when the original GET or HEAD would otherwise
return 200 (OK) for an existing selected representation. Leave redirects and failures unchanged. A cache must first
determine that it can serve the selected stored response and apply its own cache rules. A forwarding intermediary that
cannot serve the representation must not evaluate the preconditions and must pass them on if it forwards the request. See
[RFC 9110 §13.2.1](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.2.1) and
[RFC 9111 §4.3.2](https://www.rfc-editor.org/rfc/rfc9111.html#section-4.3.2).

Pass the original method and the validators for the selected representation, not a different coding's validators.
An absent ETag still permits `If-None-Match: *` to match that existing representation. For `If-Range`, a date must be
known to be strong and match the selected representation's date exactly; tag comparison is strong. See
[RFC 9110 §8.8.2.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-8.8.2.2) and
[RFC 9110 §13.1.5](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.5).

The evaluator does not read an entity or build a response. On `NOT_MODIFIED`, send a 304 with the applicable cache metadata
for the same representation (including any `Vary` the 200 would include) and no body. `EVALUATE_RANGE` permits the caller
to consider a modeled Range but does not make it satisfiable or turn it into a 206 response. If `DELEGATE` is returned,
retain the request's preconditions and use the caller's existing handling. This opt-in helper does not change `conditional`
or routing defaults.
